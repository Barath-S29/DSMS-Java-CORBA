package server;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import ShareMarket.Server;
import ShareMarket.ServerHelper;

public class NewYorkServer {
    public static void main(String[] args) {
        try {
            // Initialize the ORB
            ORB orb = ORB.init(args, null);

            // Get reference to the RootPOA
            POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

            // Activate the POA manager
            rootPOA.the_POAManager().activate();

            // Create server implementation
            ShareMarketServerImpl serverImpl = new ShareMarketServerImpl("NewYork",5000);
            ShareMarketServerImpl.setORB(orb);      //main_error trouble (Did not initialise the orb value) due to which naming service was not working. (Not able to fetch market name from directory)

            // Get object reference from the servant
            org.omg.CORBA.Object ref = rootPOA.servant_to_reference(serverImpl);
            Server serverRef = ServerHelper.narrow(ref);

            // Get the name service reference
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            // Bind the object reference in naming
            String name = "NewYorkShareMarketServer";
            NameComponent[] path = ncRef.to_name(name);
            ncRef.rebind(path, serverRef);

            System.out.println("NewYork ShareMarket Server ready in UDP port 5000");
            serverImpl.addRemoteServer("London", 5001);         // Initialising the servers in a map(key : value -> city:port)
            serverImpl.addRemoteServer("Tokyo", 5002);          // Didn't initialise this and error (for udp connection)

            // Start UDP server thread
            UDPServerThread udpThread = new UDPServerThread(5000, serverImpl, serverImpl.getShareDatabase());
            udpThread.start();

            // Client invocations....
            orb.run();
        } catch (Exception e) {
            System.err.println("ERROR: " + e);
            e.printStackTrace(System.out);
        }
    }
}