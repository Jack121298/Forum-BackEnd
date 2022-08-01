import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class ServerController
{



    public ServerController(int port) throws IOException
    {
        address = InetAddress.getByName("localhost");
        socket = new DatagramSocket(port, address);
        tcpSocket = new ServerSocket(port);
        accounts = new ArrayList<>();
        threads = new ArrayList<>();

        packetsToStore = new ArrayList<>();

        clients = new HashMap<>();
    }


    public void startServer()
    {
        listen();
        execute();
    }

    public synchronized void listen()//runs indefinitely as data is not local and can be missed
    {
        new Thread(() ->
        {
            while(true)
            {
                byte[] buffer = new byte[512];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, address, socket.getLocalPort());
                try
                {
                    socket.receive(datagramPacket);
                    packetsToStore.add(datagramPacket);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void execute()
    {
        TimerTask changeSettings = new TimerTask()
        {
            @Override
            public void run()
            {
                while(packetsToStore.size() > 0)
                {
                    try
                    {
                        parsePacket(packetsToStore.remove(packetsToStore.size()-1));
                    }
                    catch (InterruptedException | IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        };

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(changeSettings, 20, 20, TimeUnit.MILLISECONDS);

    }


    public void parsePacket(DatagramPacket datagramPacket) throws InterruptedException, IOException
    {
        StringBuilder stringBuilder = new StringBuilder();
        byte[] addrToBeConverted = address.getAddress();
        for (byte b : addrToBeConverted)
        {
            stringBuilder.append(b);
        }
        stringBuilder.append(datagramPacket.getPort());
        distributePacket(datagramPacket, checkInHashMaps(stringBuilder.toString()), stringBuilder.toString());
    }


    public int checkInHashMaps(String s)
    {
        if(clients.containsKey(s))
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }

    //General thoughts
    //We have packets coming in
    //We view the contents of the packet, check if that packet's content is already in the system and then add a packet to the
    //Account that value is storing
    //Essentially, the IP + PORT as a String maps to a client controller
    //which has logic where it is saving the current state it is in for future packets
    //Each Client Controller will have two modes, Auth and Command
    //Each Client Controller needs to check if a new Account is already existing
    //And so, Client Controller will receive -> Two ArrayLists, one for current 'in-use' accounts
    //And another for DatagramPackets to be added to be sent back

    //The problem with using sockets in threads was that from a client side, we had no way to know where the
    //Packet would be directed to as the server has multiple threads running and no way to differentiate
    //whereas with clients, they have a unique IP+PORT number which can just be used to execute socket.send(some packet to correct client)

    //Income stream is the only issue and must be fed from one thread and then redirected to the correct place
    //Outgoing data can simply be returned by looking at the client data stored locally within that class for
    //the socket, address and port!

    //XIT commands are also viewed here as we require a greater amount of control over the server.
    //XIT will be actively searched for every incoming packet that will be sent to preexisting ClientControllers or choice 1 below.
    //IF found
    //The function will get the account from the thread the packet is aiming for
    //remove it from the list of accounts which indicate that user is logged in
    //then the function will remove the data from the hashmap to free up memory- Into The Garbage You Go! GC has FOOD!
    //XIT command will cause client to end as well.
    public void distributePacket(DatagramPacket datagramPacket, int choice, String key)
    {

        //XIT problem
        //If users don't make account whilst in AUTH mode on the server
        //and then happen to exit out of program prematurely
        //memory leak
        //XIT is not used in AUTH part of assignment
        //spec states that control c will not occur and neither will any premature stoppage of server/client
        //Assuming this will be fine, just don't like the existence of this issue.
        //may need to implement a timer that for Threads, account on accounts list, and key value pairs, will delete them once
        //exceeded some amount of time without interaction
        //This will cause users to log out server side but not client side so some sort of packet indicating this process has taken
        //place will be required to reset the client
        //all of this I am assuming is outside of scope of the assignment, however

        if(choice == 0)//requires account creation for stepping through authentication stage
        {
            ClientController cc = new ClientController(socket, tcpSocket, datagramPacket, accounts, threads);
            cc.start();
            cc.givePacket(datagramPacket);
            System.out.println("KEY (IP|PORT) : "+ key);
            clients.put(key, cc);
        }
        else if(choice == 1)
        {

            clients.get(key).givePacket(datagramPacket);
            if(isXIT(datagramPacket))
            {
                clients.remove(key);
            }
        }
    }

    public boolean isXIT(DatagramPacket datagramPacket)
    {
        String command = new String(datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength()).trim();
        return command.equals("XIT");
    }

    //running instance connected to the server
    //each instance of some sort of Threaded class maps to some client by that clients IP + PORT which is always unique!
    //this is used for distribution of data to the correct place whilst using one port
    //I ran into an issue where multiple threads would call the receive() function and the data would go to the wrong thread
    //This ensures correct data distribution to the proper place
    private final HashMap<String, ClientController> clients;
    private static ArrayList<DatagramPacket> packetsToStore;
    private final ArrayList<MessageThread> threads;
    private final ArrayList<Account> accounts;
    private final DatagramSocket socket;
    private final ServerSocket tcpSocket;
    private final InetAddress address;



}
