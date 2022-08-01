import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ClientController extends Thread
{
    public ClientController(DatagramSocket socket, ServerSocket tcpSocket, DatagramPacket datagramPacket, ArrayList<Account> accounts, ArrayList<MessageThread> threads)
    {
        this.socket = socket;
        this.tcpSocket = tcpSocket;
        address = datagramPacket.getAddress();
        port = datagramPacket.getPort();
        this.accounts = accounts;
        buffer = new ArrayList<>();
        mode = "AUTH";
        state = "USERNAME";
        this.threads = threads;

        raf = null;
        osw = null;
        try
        {
            raf = new RandomAccessFile("credentials.txt", "rw");
            osw = new OutputStreamWriter(new FileOutputStream("credentials.txt", true));
        }
        catch (FileNotFoundException e)
        {
            System.out.println("cannot create file in this folder");
            System.exit(1);
        }
    }


    public void run()
    {
        System.out.println("Waiting for user activity");
        while(true)//change to condition later where client catches System.exit and sends packet before closing
        {

            if(buffer.isEmpty())
            {
                try
                {
                    awaitPacket();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            if (mode.equals("AUTH"))//password and account creation
            {
                try
                {
                    incrementAUTH_DFA();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else if(mode.equals("COMMAND"))
            {
                try
                {
                    determineCOMMAND();
                }
                catch (IOException | InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }


    //works based on assumptions of already completed steps
    public void incrementAUTH_DFA() throws IOException
    {
        byte[] responseB = new byte[1];
        DatagramPacket response = new DatagramPacket(responseB, responseB.length, address, port);
        DatagramPacket packet = buffer.remove(0);


        //first check if this is a username by seeing if name has been entered
            //check if username is already logged in with arraylist of logged in accounts
            //if match found, send back error packet
        //skip if done
        //then enter password
        //check success
        //if success -> enter COMMAND mode


        switch (state)
        {
            case "USERNAME":
                System.out.println("Client authenticating");
                String name = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
                //now need function to send packet back corresponding to the code in client
                //make sure they match!
                int result = checkAccount(name);
                if (result == 0)//create new account
                {
                    responseB[0] = 0;
                    state = "CREATION_DECISION";
                    tempName = name;
                    socket.send(response);
                }
                else if (result == 1)//reject login due to account already being logged in
                {
                    System.out.println(name + "has already logged in!");
                    responseB[0] = 1;
                    socket.send(response);
                    state = "USERNAME";
                }
                else//move forward to enter password
                {
                    System.out.println("Password Request");
                    responseB[0] = 2;
                    tempName = name;
                    state = "PASSWORD";
                    socket.send(response);
                }
                break;
            case "CREATION_DECISION":
                if (packet.getData()[0] == 0)
                {
                    state = "USERNAME";
                    tempName = "";
                }
                else
                {
                    System.out.println("New Account being created");
                    state = "CREATION";
                    socket.send(response);
                }
                break;
            case "CREATION":
            {
                String password = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
                account = new Account(tempName, password);
                accounts.add(account);
                osw.append(tempName).append(" ").append(password);
                osw.append(System.getProperty("line.separator"));
                osw.flush();
                System.out.println(tempName+ " successful login");
                mode = "COMMAND";
                break;
            }
            case "PASSWORD":
            {
                String password = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
                if (accountExists(tempName, password))
                {
                    responseB[0] = 1;
                    mode = "COMMAND";
                    account = new Account(tempName, password);
                    accounts.add(account);
                    System.out.println(tempName+ " successful login");
                }
                else
                {
                    System.out.println(tempName+ " failed login");
                    responseB[0] = 0;
                    state = "USERNAME";
                }
                socket.send(response);
                break;
            }
        }
    }



    public void awaitPacket() throws InterruptedException
    {
        synchronized (this)
        {
            if(buffer.isEmpty())
            {
                System.out.println("Waiting for user activity");
                this.wait();
            }
        }
    }


    public void givePacket(DatagramPacket data)
    {
        buffer.add(data);
        synchronized (this)
        {
            this.notify();
        }
    }

    public int checkAccount(String name) throws IOException
    {
        for(Account account : accounts)
        {
            if (name.equals(account.getName()))//found user logged in
            {
                return 1;
            }
        }
        if(accountExists(name))//preexisting account
        {
            return 2;
        }
        return 0;//new account must be made
    }



    public boolean accountExists(String name, String password) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(" ");
        sb.append(password);
        String currentLine;
        raf.seek(0);
        while(true)
        {
            try
            {
                currentLine = raf.readLine();
                if (currentLine != null && currentLine.equals(sb.toString()))
                {
                    return true;
                }
                else if(currentLine == null)
                {
                    break;
                }
            }
            catch(EOFException e)
            {
                break;
            }
        }
        return false;
    }


    public boolean accountExists(String name) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        String currentLine;
        raf.seek(0);

        while(true)
        {
            try
            {
                currentLine = raf.readLine();
                if (currentLine != null)
                {
                    currentLine = getParameter(currentLine, 0);
                    if(currentLine.equals(sb.toString()))
                    {
                        return true;
                    }
                }
                else
                {
                    break;
                }
            }
            catch(EOFException e)
            {
                break;
            }
        }
        return false;
    }



    public void determineCOMMAND() throws IOException, InterruptedException
    {
        byte[] responseB = new byte[1];
        DatagramPacket response = new DatagramPacket(responseB, responseB.length, address, port);
        DatagramPacket packet = buffer.remove(0);
        String command = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        if(command.length() >= 3)
        {
            String code = command.substring(0, 3);
            distributeCodeToCorrectFunction(code, packet);
        }
        else
        {
            socket.send(response);
        }
    }



    private void distributeCodeToCorrectFunction(String code, DatagramPacket packet) throws IOException
    {
        System.out.println(tempName+ " issued " + code + " command");
        switch (code)
        {
            case "CRT":
                commandCRT(packet);
                break;
            case "MSG":
                commandMSG(packet);
                break;
            case "LST":
                commandLST();
                break;
            case "RDT":
                commandRDT(packet);
                break;
            case "EDT":
                commandEDT(packet);
                break;
            case "DLT":
                commandDLT(packet);
                break;
            case "RMV":
                commandRMV(packet);
                break;
            case "UPD":
                tcpFileTransfer(packet, 1);
                break;
            case "DWN":
                tcpFileTransfer(packet, 2);
                break;
            case "XIT":
                commandXIT();
                break;
        }
    }

    public void commandCRT(DatagramPacket packet) throws IOException
    {
        String data = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        data = data.substring(4);

        boolean hasThread = false;
        for(MessageThread thread : threads)
        {
            if(thread.name().equals(data))
            {
                hasThread = true;
                byte[] buffer = new byte[512];
                DatagramPacket error = new DatagramPacket(buffer, buffer.length, address, port);
                socket.send(error);
                System.out.println("Thread " + data + " exists");
            }
        }
        if(!hasThread)//Single error to worry about
        {
            byte[] buffer = new byte[512];
            buffer[0] = 1;
            DatagramPacket success = new DatagramPacket(buffer, buffer.length, address, port);
            MessageThread mt = new MessageThread(tempName, data);
            threads.add(mt);
            socket.send(success);
            System.out.println("Thread " + data + " created");
        }

    }

    public void commandMSG(DatagramPacket packet) throws IOException
    {
        String data = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        String threadName = getParameter(data, 4);
        String message = data.substring(4 + threadName.length() + 1);

        boolean hasThread = false;
        for(MessageThread thread : threads)
        {
            if(thread.name().equals(threadName))
            {
                hasThread = true;
                byte[] buffer = new byte[1];
                buffer[0] = 1;
                DatagramPacket success = new DatagramPacket(buffer, buffer.length, address, port);
                thread.writeMessage(tempName, message);
                socket.send(success);
                System.out.println("Message posted to " + threadName + " thread");
            }
        }
        if(!hasThread)//error message
        {
            byte[] buffer = new byte[1];
            DatagramPacket error = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(error);
            System.out.println("Thread does not exist");
        }
    }

    public void commandLST() throws IOException
    {
        if(catchEmptyThreadList())
        {
            byte[] response = new byte[512];
            DatagramPacket data = new DatagramPacket(response, response.length, address, port);
            socket.send(data);
            System.out.println("Threads do not exist");
        }
        else
        {
            StringBuilder stringBuilder = new StringBuilder();
            for (MessageThread mt : threads)
            {
                stringBuilder.append(mt.name()).append("\n");
            }
            byte[] response = stringBuilder.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket data = new DatagramPacket(response, response.length, address, port);
            socket.send(data);
            System.out.println("List of Threads have been sent to client");
        }
    }

    public void commandRDT(DatagramPacket packet) throws IOException
    {
        String entireBuffer = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        String threadName = entireBuffer.substring(4);
        String threadData = null;

        for(MessageThread mt : threads)
        {
            if(mt.name().equals(threadName))
            {
                threadData = mt.readEntireThread();
            }
        }

        if(threadData != null)//working
        {
            System.out.println("Thread " + threadName + " is being read");
            byte[] data = threadData.getBytes(StandardCharsets.UTF_8);
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(datagramPacket);
        }
        else//empty thread
        {
            System.out.println("Thread " + threadName + " can not be found");
            byte[] data = new byte[1];
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(datagramPacket);
        }
    }

    public void commandEDT(DatagramPacket packet) throws IOException
    {
        String entireBuffer = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        String threadName = getParameter(entireBuffer,4);
        String messageNumber = getParameter(entireBuffer,4 + threadName.length() + 1);
        String message = entireBuffer.substring(threadName.length() + messageNumber.length() + 6);

        MessageThread temp = getThreadByName(threadName);
        if(temp == null)//empty thread
        {
            System.out.println("Thread " + threadName + " can not be found");
            byte[] data = new byte[1];
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(datagramPacket);
        }
        else
        {
            int result = temp.editThread(tempName, messageNumber, message);
            if(result == 1)
            {
                System.out.println("Message number given is not valid");
                byte[] data = new byte[1];
                data[0] = 1;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
            }
            else if (result == 2)//auth
            {
                System.out.println("You do not have permission to do that");
                byte[] data = new byte[1];
                data[0] = 2;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
            }
            else//success
            {
                System.out.println("Message at location " + messageNumber + " in Thread " + threadName + " has been edited successfully");
                byte[] data = new byte[1];
                data[0] = 3;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
            }
        }
    }

    public void commandDLT(DatagramPacket packet) throws IOException
    {
        String entireBuffer = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        String threadName = getParameter(entireBuffer,4);
        String messageNumber = getParameter(entireBuffer,4 + threadName.length() + 1);

        //need empty thread, No message at number and no auth error

        MessageThread temp = getThreadByName(threadName);
        if(temp == null)//empty thread
        {
            System.out.println("Thread " + threadName + " can not be found");
            byte[] data = new byte[1];
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(datagramPacket);
        }
        else
        {
            System.out.println("Message in " + threadName + " can not be found");
            int result = temp.deleteMessage(tempName, messageNumber);

            if(result == 1)
            {
                System.out.println("Message number given is not valid");
                byte[] data = new byte[1];
                data[0] = 1;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
            }
            else if (result == 2)//auth
            {
                System.out.println("You do not have permission to do that");
                byte[] data = new byte[1];
                data[0] = 2;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
            }
            else//success
            {
                System.out.println("Message at location " + messageNumber + " in Thread " + threadName + " has been deleted successfully");
                byte[] data = new byte[1];
                data[0] = 3;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
            }
        }
    }

    public void commandRMV(DatagramPacket packet) throws IOException
    {

        String entireBuffer = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        String threadName = getParameter(entireBuffer,4);
        byte[] data = new byte[1];

        //need error for auth and no thread

        MessageThread temp = getThreadByName(threadName);
        if(temp == null)
        {
            System.out.println("Thread " + threadName + " can not be found");
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
            socket.send(datagramPacket);
        }
        else
        {
            if(!temp.hasPermissionToDeleteFile(tempName))//fail auth
            {
                System.out.println("You do not have permission to do that");
                data[0] = 1;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
            }
            else//success
            {
                temp.deleteFilesUploadedToThread(tempName, threadName);
                System.out.println("Thread " + threadName + " has been removed successfully");
                data[0] = 2;
                DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);
                socket.send(datagramPacket);
                threads.remove(temp);
            }
        }
    }

    public void tcpFileTransfer(DatagramPacket packet, int option) throws IOException
    {
        String entireBuffer = new String(packet.getData(), packet.getOffset(), packet.getLength()).trim();
        String threadName;
        String fileName;

        threadName = getParameter(entireBuffer, 4);
        fileName = getParameter(entireBuffer,4 + threadName.length() + 1);
        byte[] data = new byte[1];
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length, address, port);

        int portToStartTCPSocket = socket.getLocalPort();

        //need error for thread doesn't exist, file already exists,
        MessageThread temp = getThreadByName(threadName);
        if(temp == null)//empty thread
        {
            System.out.println("Thread " + threadName + " can not be found");
            socket.send(datagramPacket);
        }
        else
        {
            if(option == 1)
            {
                if(new File(threadName+"-"+fileName).exists())
                {
                    data[0] = 1;
                    socket.send(datagramPacket);
                    System.out.println(fileName + " already exists!");
                }
                else
                {
                    data[0] = 2;
                    temp.upd(tcpSocket, threadName, fileName);
                    socket.send(datagramPacket);
                    temp.writeFileUploaded(tempName, fileName);
                    System.out.println("Upload of " + fileName + " has been completed");
                }
            }
            else if(option == 2)
            {
                if(!new File(threadName+"-"+fileName).exists())
                {
                    System.out.println(fileName + " does not exist!");
                    data[0] = 1;
                    socket.send(datagramPacket);
                }
                else
                {
                    data[0] = 2;
                    socket.send(datagramPacket);
                    temp.dwn(tcpSocket, threadName, fileName);
                    System.out.println("Download of " + fileName + " has been completed");
                }
            }
        }
    }

    public void commandXIT()
    {
        System.out.println(tempName + " exited");
        accounts.remove(account);
    }

    public boolean catchEmptyThreadList()
    {
        return threads.isEmpty();
    }

    public String getParameter(String data, int startIndex)
    {
        int index;
        for(index = startIndex ; index < data.length(); index++)
        {
            if(data.charAt(index) == ' ')
            {
                break;
            }
        }
        return data.substring(startIndex, index);
    }

    public MessageThread getThreadByName(String name)
    {
        for(MessageThread mt : threads)
        {
            if(mt.name().equals(name))
            {
                return mt;
            }
        }
        return null;
    }


    private final ArrayList<Account> accounts;
    private final ArrayList<DatagramPacket> buffer;
    private final ArrayList<MessageThread> threads;
    private final InetAddress address;
    private final DatagramSocket socket;
    private final ServerSocket tcpSocket;
    private final int port;
    private Account account;
    private String mode;
    private String state;
    private String tempName;
    private RandomAccessFile raf;
    private OutputStreamWriter osw;
}