import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.IllegalBlockingModeException;

public class MessageThread
{
    public MessageThread(String name, String fileName) throws IOException
    {
        this.fileName = fileName;
        file = new File(fileName+".txt");
        createFile(name);
    }

    public String name()
    {
        return fileName;
    }

    public void createFile(String name) throws IOException
    {
        if(file.createNewFile() && file.setWritable(true))
        {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, true));
            writer.append(name);
            writer.append("\n");
            writer.flush();
            writer.close();
        }
    }

    public void writeMessage(String name, String message) throws IOException
    {
        String line = ++messageIndex + " " + name + ": " + message + "\n";
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, true));
        writer.append(line);
        writer.flush();
        writer.close();
    }

    public String readEntireThread() throws IOException
    {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(file));
        br.readLine();
        while (br.ready())
        {
            sb.append(br.readLine());
            sb.append(System.getProperty("line.separator"));
        }
        br.close();
        return sb.toString();
    }

    public int editThread(String name, String number, String altMessage) throws IOException
    {
        int num = Integer.parseInt(number);
        if(num > messageIndex)
        {
            return 1;//number was not correct
        }

        boolean madeChange = false;
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String phrase = number + " " + name + ":";
        String line;
        StringBuilder sb = new StringBuilder();

        while ((line = bufferedReader.readLine()) != null)
        {
            int index = 0;
            for(int i = 0; i < line.length(); i++)
            {
                if(line.charAt(i) == ':')
                {
                    index = i;
                    break;
                }
            }
            String lineBegin = line.substring(0, index+1);
            if(lineBegin.equals(phrase))
            {
                sb.append(phrase).append(" ").append(altMessage);
                sb.append("\n");
                madeChange = true;
            }
            else
            {
                sb.append(line).append("\n");
            }
        }
        if(madeChange)
        {
            FileOutputStream fileOut = new FileOutputStream(file);
            fileOut.write(sb.toString().getBytes());
            fileOut.close();
            bufferedReader.close();
            return 3;//success
        }
        bufferedReader.close();
        return 2;//no auth
    }

    public int deleteMessage(String name, String number) throws IOException
    {
        int num = Integer.parseInt(number);
        if(num > messageIndex)
        {
            return 1;
        }

        boolean foundLineToDelete = false;
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String phrase = number + " " + name + ":";
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = bufferedReader.readLine()) != null)
        {
            int index = 0;
            for(int i = 0; i < line.length(); i++)
            {
                if(line.charAt(i) == ':')
                {
                    index = i;
                    break;
                }
            }
            String lineBegin = line.substring(0, index+1);
            if(lineBegin.equals(phrase))
            {
                foundLineToDelete = true;
            }
            else
            {
                if(foundLineToDelete)
                {
                    sb.append(decrementStringIfNumberExists(line)).append("\n");
                }
                else
                {
                    sb.append(line).append("\n");
                }
            }
        }

        FileOutputStream fileOut = new FileOutputStream(file);
        fileOut.write(sb.toString().getBytes());
        fileOut.close();
        bufferedReader.close();

        if(foundLineToDelete)
        {
            return 3;
        }
        return 2;
    }

    public String decrementStringIfNumberExists(String line)
    {
        int whitespaceMark = 0;
        for(int i = 0; i < line.length(); i++)
        {
            if(line.charAt(i) == ' ')
            {
                whitespaceMark = i;
                break;
            }
        }

        String numberPart = line.substring(0, whitespaceMark);
        int number = Integer.parseInt(numberPart);
        number--;
        numberPart = Integer.toString(number);
        return numberPart + " " + line.substring(whitespaceMark + 1);
    }


    public boolean hasPermissionToDeleteFile(String name) throws IOException
    {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        if(name.equals(bufferedReader.readLine()))
        {
            bufferedReader.close();
            return true;
        }
        bufferedReader.close();
        return false;
    }

    public void deleteFilesUploadedToThread(String name, String threadName) throws IOException
    {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String nextLine;
        while(bufferedReader.ready())
        {
            nextLine = bufferedReader.readLine();
            if(nextLine.length() >= name.length() + 10)
            {
                String subString = nextLine.substring(0, name.length() + 10);
                if(subString.equals(name + " uploaded "))
                {
                    if(nextLine.length() > subString.length())
                    {
                        String secondSubstring = nextLine.substring(subString.length());
                        File newFile = new File(threadName + "-" + secondSubstring);
                        newFile.delete();
                    }
                }
            }
        }
        bufferedReader.close();
        file.delete();

    }


    public void upd(ServerSocket serverSocket, String threadTitle, String nameOfNewFile)
    {
        new Thread(() ->
        {
            Socket socket = null;
            InputStream in = null;
            OutputStream out = null;

            try
            {
                socket = serverSocket.accept();
                in = socket.getInputStream();
            }
            catch (IOException ex)
            {
                System.out.println("Can't accept client connection. ");
            }

            try
            {
                out = new FileOutputStream(threadTitle+"-"+nameOfNewFile);
            }
            catch (FileNotFoundException ex)
            {
                System.out.println("File not found. ");
            }
            byte[] bytes = new byte[8192];

            int count;
            try
            {
                if(in != null && out != null)
                {
                    while ((count = in.read(bytes)) > 0)
                    {
                        out.write(bytes, 0, count);
                    }
                    out.close();
                    in.close();
                }
                assert socket != null;
                socket.close();
            }
            catch (IOException e)
            {
                System.out.println("Error");
            }
        }).start();
    }

    public void writeFileUploaded(String name, String fileNameWithExtension) throws IOException
    {
        String line = name + " uploaded " + fileNameWithExtension + "\n";
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, true));
        writer.append(line);
        writer.flush();
        writer.close();
    }

    public void dwn(ServerSocket serverSocket, String threadTitle, String nameOfNewFile)
    {
        new Thread(() ->
        {
            Socket socket = null;
            InputStream in = null;
            OutputStream out = null;

            try
            {
                socket = serverSocket.accept();
                out = socket.getOutputStream();
            }
            catch (IOException ex)
            {
                System.out.println("Can't accept client connection. ");
            }
            try
            {
                in = new FileInputStream(threadTitle+"-"+nameOfNewFile);
            }
            catch (FileNotFoundException ex)
            {
                System.out.println("File not found. ");
            }
            byte[] bytes = new byte[8192];
            int count;
            try
            {
                if(in != null && out != null)
                {
                    while ((count = in.read(bytes)) > 0)
                    {
                        out.write(bytes, 0, count);
                    }
                    out.close();
                    in.close();
                }
                assert socket != null;
                socket.close();
            }
            catch (IOException e)
            {
                System.out.println("Error");
            }

        }).start();
    }






    private final String fileName;
    private final File file;
    private int messageIndex;
}
