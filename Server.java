import java.io.IOException;

public class Server
{

    public static void main(String[] args) throws IOException
    {
        int port = Integer.parseInt(args[0]);
        //int port = 5000;
        ServerController sc = new ServerController(port);
        sc.startServer();
    }
}