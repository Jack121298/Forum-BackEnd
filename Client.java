import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

//new Class Client
public class Client
{
    //main running function
    public static void main(String[] args) throws IOException
    {
        //port given by user
        int port = Integer.parseInt(args[0]);
        //test port
        //int port = 5000;
        //function to save port for later use
        saveConnectionData(port);
        //start authentication step
        authenticate();
    }



    //save port to the class and also save address to reduce line length, a lot easier to type 'address' vs InetAddress.getByName("localhost");
    //create new UDP socket and save locally. Open-ended port that OS chooses
    public static void saveConnectionData(int port) throws SocketException, UnknownHostException
    {
        socket = new DatagramSocket();
        address = InetAddress.getByName("localhost");
        serverPort = port;
    }



    //authentication step function
    //waits for server to authorize user
    public static void authenticate() throws IOException
    {
        while(true)
        {
            Scanner sc = new Scanner(System.in);//new scanner for user input
            String name;//username string
            System.out.println("Enter username: ");//prompt user for name
            name = sc.nextLine();//save user input to string

            while (name.length() == 0)//if user enters string of size 0 and continues to do so
            {
                System.out.println("Enter username of sufficient size: ");//prompt user to enter valid username
                name = sc.nextLine();//save and then recheck
            }

            byte[] buffer = name.getBytes();//save bytes to buffer array
            byte[] resBuffer = new byte[1];//new response buffer for what the server responds back with
            DatagramPacket data = new DatagramPacket(buffer, buffer.length, address, serverPort);//two packets for outgoing data and incoming response
            DatagramPacket response = new DatagramPacket(resBuffer, resBuffer.length, address, serverPort);
            //send username as a packet
            socket.send(data);
            //receive response and save to resBuffer
            socket.receive(response);

            if(response.getData()[0] == 0)//account creation, query if user wants to make account and then query password
            {

                //send off response back to server on whether user wants to create account with that name
                //if response is false -> break loop
                //if true -> server will request password as it already has the name of the account
                System.out.println("No account found, Do you wish to create an account with that name? (YES/NO)");
                String query = sc.nextLine();
                accountCreation(query);//give query to function and determine user response
            }
            else if(response.getData()[0] == 1)//already logged in, try again
            {
                System.out.println("User is already logged in");
            }
            else if(response.getData()[0] == 2)//query password
            {
                //enter new function to send back password and check if password is incorrect
                passwordQuery();
            }
        }
    }



    //function to indicate that login is successful based off responses from server
    //starts server commands function
    public static void loginSuccess() throws IOException
    {
        System.out.println("Welcome to the forum");
        serverCommands();
    }


    //function to determine if user would like to create account
    //proceeds to create if YES is given
    //if NO or some other input, then return to log in function
    public static void accountCreation(String query) throws IOException
    {
        byte[] resBuffer = new byte[1];//new response buffer
        DatagramPacket response = new DatagramPacket(resBuffer, resBuffer.length, address, serverPort);//new packet to give to server
        Scanner sc = new Scanner(System.in);//check if user says YES or otherwise
        if(query.contains("YES"))//if yes
        {
            resBuffer[0] = 1;//write 1 to buffer at index 0
            socket.send(response);//send
            socket.receive(response);//receive response from server
            System.out.println("Enter new password: ");//query for new password from user
            String password = sc.nextLine();//read and save user input
            byte[] buffer = password.getBytes();//write password to byte array
            DatagramPacket data = new DatagramPacket(buffer, buffer.length, address, serverPort);//package as data packet
            socket.send(data);//send password to server
            loginSuccess();//indicate login was successful
        }
        else//NO or otherwise
        {
            response = new DatagramPacket(resBuffer, resBuffer.length, address, serverPort);//write response containing 0 from instantiation at index 0
            socket.send(response);//send to server indicating to return to login page
        }
    }


    //query for password upon giving server username
    public static void passwordQuery() throws IOException
    {
        byte[] resBuffer = new byte[1];//defaults to 0
        Scanner sc = new Scanner(System.in);//new scanner
        DatagramPacket response = new DatagramPacket(resBuffer, resBuffer.length, address, serverPort);//new packet to give to server
        System.out.println("Enter password: ");//prompt user for password for preexisting account
        String password = sc.nextLine();//read data
        byte[] buffer = password.getBytes();//save to buffer

        DatagramPacket data = new DatagramPacket(buffer, buffer.length, address, serverPort);//package as packet
        socket.send(data);//give to server
        socket.receive(response);//receive response
        //check validity
        if(response.getData()[0] == 1)//if 1
        {
            loginSuccess();//successful
        }
        else
        {
            System.out.println("Wrong password! Try again!");//else return to log in after telling user login has failed
        }
    }


    //server command function
    public static void serverCommands() throws IOException
    {
        //loop eternally until exit is given
        Scanner scanner = new Scanner(System.in);//new scanner

        while(true)//loop indefinitely
        {
            printCommands();//print list of commands to user
            String data = scanner.nextLine();//scan next line
            data = data.trim();//trim any whitespace
            if(!socket.isConnected())//check if socket is connected
            {
                socket.connect(address, serverPort);//connect if not
            }

            if((data.length() >= 3) && isWellFormedString(data))//check if string is of at least size 3 and is a wellFormed String for commands to give to server
            {
                    String command = data.substring(0, 3);//substring from 0 to 3 for every command
                    switch (command)//give switch to compare the commands of all ten types
                    {
                        case "XIT":
                            caseXIT(data);
                            break;
                        case "CRT":
                            caseCRT(data);
                            break;
                        case "MSG":
                        {
                            caseMSG(data);
                            break;
                        }
                        case "LST":
                        {
                            caseLST(data);
                            break;
                        }
                        case "RDT":
                        {
                            caseRDT(data);
                            break;
                        }
                        case "EDT":
                        {
                            caseEDT_DLT(1, data);//cheap flag because both are very similar
                            break;
                        }
                        case "DLT":
                        {
                            caseEDT_DLT(2, data);//cheap flag because both are very similar
                            break;
                        }
                        case "RMV":
                        {
                            caseRMV(data);
                            break;
                        }
                        case "UPD":
                        {
                            caseUPD(data);
                            break;
                        }
                        case "DWN":
                        {
                            caseDWN(data);
                            break;
                        }
                    }
                }
            else
            {
                System.out.println("Try again");//try again if is not a well-formed string or size is less than 3
            }
        }
    }


    //check well formed string
    //could be made shorted quite easily, just ran out of time
    public static boolean isWellFormedString(String input)
    {
        String command = input.substring(0, 3);//new substring for checking commands
        int numberOfWS = input.length() - input.replaceAll(" ","").length();//save whitespace count
        if(command.equals("XIT") || command.equals("LST") && input.length() == 3)//if these commands and the length is of size 3
        {
            return true;//return true
        }
        else if(command.equals("CRT") || command.equals("RDT") || command.equals("RMV"))//if these commands
        {
            //check whitespace count where it should equal one
            //if ws == 1 and exists at index 3 and the length of string 'input' from index 4 to max length is > 0
            //return true and valid
            return numberOfWS == 1 && input.charAt(3) == ' ' && input.substring(4).length() > 0;
        }
        else if(command.equals("MSG") || command.equals("DLT") || command.equals("UPD") || command.equals("DWN") || command.equals("EDT"))//else if these commands
        {
            if(numberOfWS == 2 && input.charAt(3) == ' ' && input.substring(4).length() > 0)//same as before but with 2 WS
            {
                int secondWS;
                for(secondWS = 4; secondWS < input.length(); secondWS++)
                {
                    if(input.charAt(secondWS) == ' ')//finding second WS location
                    {
                        break;
                    }
                }
                //from second WS the string length is > 0
                if(input.substring(secondWS).length() > 0)
                {
                    if(!command.equals("DLT"))//only if not DLT command
                    {
                        return true;//return true
                    }
                    else if(isNumberInString(input.substring(secondWS+1))) return true;//now check if 2nd word in DLT command given can be converted to an integer for the server and return true if possible
                }
            }
            //now check if command is MSG and number of WS is >= as MSG and EDT can have an undefined WS count max but must be a minimum of 2 for MSG and 3 for EDT
            else if(command.equals("MSG") && numberOfWS >= 2 && input.charAt(3) == ' ' && input.substring(4).length() > 0)
            {
                int secondWS;//find second WS
                for(secondWS = 4; secondWS < input.length(); secondWS++)
                {
                    if(input.charAt(secondWS) == ' ')
                    {
                        break;
                    }
                }
                if(input.substring(secondWS).length() > 0) return true;//check if length of word is greater than 0 from secondWS
            }
            else if(numberOfWS >= 3 && input.charAt(3) == ' ' && input.substring(4).length() > 0)//check if number of WS is greater than of eq to 3 and other checks from above
            {
                int secondWS;//find second WS
                for(secondWS = 4; secondWS < input.length(); secondWS++)
                {
                    if(input.charAt(secondWS) == ' ')
                    {
                        break;
                    }
                }

                int thirdWS;//find third ws
                for(thirdWS = secondWS+1; thirdWS < input.length(); thirdWS++)
                {
                    if(input.charAt(thirdWS) == ' ')
                    {
                        break;
                    }
                }
                //if is a number at 3rd word and string from index of 3rd word is greater than 0
                if(isNumberInString(input.substring(secondWS+1, thirdWS)) && input.substring(thirdWS).length() > 0)
                {
                    return true;//return true
                }
            }
        }
        //else give error and return false
        System.out.println("Wrong input! Try Again");
        return false;
    }



    //check if string can be formed into number
    public static boolean isNumberInString(String s)
    {
        try//try
        {
            Integer.parseInt(s);//attempt
            return true;//return true if this part is reached
        }
        catch (NumberFormatException e)//catch failure
        {
            return false;
        }
    }

    //case XIT command
    public static void caseXIT(String data) throws IOException
    {
        //send command to server
        sendCommand(data);
        System.out.println("Goodbye!");//give goodbye to user
        System.exit(0);//exit client
    }



    //case CRT command
    public static void caseCRT(String data) throws IOException
    {
        //send command to server
        sendCommand(data);
        byte[] response = receiveResponse();
        if (response[0] == 1)
        {
            String name = data.substring(4);
            System.out.println("Thread " + name + " was created!");
        }
        else
        {
            System.out.println("Thread Already Exists!");
        }
    }



    public static void caseMSG(String data) throws IOException
    {
        //send command to server
        sendCommand(data);
        byte[] response = receiveResponse();

        if (response[0] == 1)
        {
            System.out.println("Message posted");
        }
        else
        {
            System.out.println("No such Thread");
        }
    }



    public static void caseLST(String data) throws IOException
    {
        //send command to server
        sendCommand(data);

        byte[] newData = receiveData();

        if (newData[0] == 0)
        {
            System.out.println("No Threads exist!");
        }
        else
        {
            String list = new String(newData, 0, newData.length).trim();
            System.out.println("The list of active Threads:");
            System.out.println(list);
        }
    }



    public static void caseRDT(String data) throws IOException
    {
        //send command to server
        sendCommand(data);

        byte[] newData = receiveData();

        if (newData[0] == 0)
        {
            System.out.println("No such Thread");
        }
        else
        {
            String thread = new String(newData, 0, newData.length).trim();
            if(thread.isEmpty() || thread.isBlank())
            {
                System.out.println("Thread is empty!");
            }
            else
            {
                System.out.println(thread);
            }
        }
    }



    public static void caseEDT_DLT(int type, String data) throws IOException
    {
        //send command to server
        sendCommand(data);
        System.out.println(data);
        byte[] response = receiveResponse();
        if (response[0] == 0)
        {
            System.out.println("No such Thread");
        }
        if (response[0] == 1)
        {
            System.out.println("Given message number not valid");
        }
        if (response[0] == 2)
        {
            System.out.println("Current user does not have access to that");
        }
        if (response[0] == 3 && type == 1)
        {
            System.out.println("Successfully edited message");
        }
        if (response[0] == 3 && type == 2)
        {
            System.out.println("Successfully deleted message");
        }
    }



    public static void caseRMV(String data) throws IOException
    {
        //send command to server

        sendCommand(data);
        byte[] response = receiveResponse();

        if (response[0] == 0)
        {
            System.out.println("No such Thread");
        }
        else if (response[0] == 1)
        {
            System.out.println("Current user does not have access to that");
        }
        else if (response[0] == 2)
        {
            System.out.println("Successfully removed thread!");
        }


    }



    public static void caseUPD(String data) throws IOException
    {
        //send command to server
        sendCommand(data);
        String fileName = getNextWord(data);
        byte[] response = receiveData();

        if (response[0] == 2)
        {
            sendFile(fileName);
            System.out.println("File Uploaded!");
        }
        else if (response[0] == 1)
        {
            System.out.println("File already exists on this thread!");
        }
        else if (response[0] == 0)
        {
            System.out.println("No such Thread");
        }
    }



    public static void caseDWN(String data) throws IOException
    {
        //send command to server
        sendCommand(data);
        String fileName = getNextWord(data);
        byte[] response = receiveData();

        if (response[0] == 2)
        {
            receiveFile(fileName);
            System.out.println("File Downloaded!");
        }
        else if (response[0] == 1)
        {
            System.out.println("File does not exist on Server!");
        }
        else if (response[0] == 0)
        {
            System.out.println("No such Thread");
        }
    }


    public static void sendFile(String fileName) throws IOException
    {
        socket.disconnect();
        Socket tcp_socket = new Socket("localhost", serverPort);
        tcp_socket.getOutputStream();
        InputStream inputStream = null;
        OutputStream fileOutputStream = null;
        byte[] myByteArray = new byte[8192];
        try
        {
            inputStream = new FileInputStream(fileName);
            fileOutputStream = tcp_socket.getOutputStream();
            int count;
            while ((count = inputStream.read(myByteArray)) > 0)
            {
                fileOutputStream.write(myByteArray, 0, count);
            }
        }
        finally
        {
            assert fileOutputStream != null;
            fileOutputStream.close();
            inputStream.close();
            tcp_socket.close();
        }
    }


    public static void receiveFile(String fileName) throws IOException
    {
        socket.disconnect();
        Socket tcp_socket = new Socket("localhost", serverPort);

        tcp_socket.getInputStream();
        InputStream inputStream = null;
        OutputStream fileOutputStream = null;
        byte[] myByteArray = new byte[8192];
        try
        {
            fileOutputStream = new FileOutputStream(fileName);
            inputStream = tcp_socket.getInputStream();
            int count;
            while ((count = inputStream.read(myByteArray)) > 0)
            {
                fileOutputStream.write(myByteArray, 0, count);
            }
        }
        finally
        {
            assert fileOutputStream != null;
            fileOutputStream.close();

            assert inputStream != null;
            inputStream.close();
            tcp_socket.close();
        }
    }


    public static void sendCommand(String data) throws IOException
    {
        byte[] commandBuffer = data.getBytes(StandardCharsets.UTF_8);
        DatagramPacket commandPacket = new DatagramPacket(commandBuffer, commandBuffer.length, address, serverPort);
        socket.send(commandPacket);
    }



    public static byte[] receiveResponse() throws IOException
    {
        byte[] resBuffer = new byte[1];//defaults to 0
        DatagramPacket responsePacket = new DatagramPacket(resBuffer, resBuffer.length, address, serverPort);
        socket.receive(responsePacket);
        return resBuffer;
    }



    public static byte[] receiveData() throws IOException
    {
        byte[] resBuffer = new byte[512];//defaults to 0
        DatagramPacket responsePacket = new DatagramPacket(resBuffer, resBuffer.length, address, serverPort);
        socket.receive(responsePacket);
        return resBuffer;
    }



    public static String getNextWord(String sentence)
    {
        int indexOfEndOFfThreadTitle = 4;
        for (int i = 4; i < sentence.length(); i++)
        {
            if (sentence.charAt(i) == ' ')
            {
                indexOfEndOFfThreadTitle = i;
                break;
            }
        }
        return sentence.substring(indexOfEndOFfThreadTitle + 1);
    }



    public static void printCommands()
    {
        System.out.println(commandList);
    }


    private static DatagramSocket socket;
    private static InetAddress address;
    private static int serverPort;
    private static final String commandList = System.getProperty("line.separator") + "Enter one of the following commands: CRT," + System.getProperty("line.separator") + "MSG, DLT, EDT, LST, RDT, UPD, DWN, RMV," + System.getProperty("line.separator") + "XIT:" + System.getProperty("line.separator");
}
