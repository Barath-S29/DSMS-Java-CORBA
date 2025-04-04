package server;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import ShareMarket.Server;
import ShareMarket.ServerHelper;

public class TokyoServer {
    public static void main(String[] args) {
        try {
            // Initialize the ORB
            ORB orb = ORB.init(args, null);

            // Get reference to the RootPOA
            POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

            // Activate the POA manager
            rootPOA.the_POAManager().activate();

            // Create server implementation
            ShareMarketServerImpl serverImpl = new ShareMarketServerImpl("Tokyo",5002);
            ShareMarketServerImpl.setORB(orb);

            // Get object reference from the servant
            org.omg.CORBA.Object ref = rootPOA.servant_to_reference(serverImpl);
            Server serverRef = ServerHelper.narrow(ref);

            // Get the name service reference
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            // Bind the object reference in naming
            String name = "TokyoShareMarketServer";
            NameComponent[] path = ncRef.to_name(name);
            ncRef.rebind(path, serverRef);

            System.out.println("Tokyo ShareMarket Server ready in UDP port 5002");
            serverImpl.addRemoteServer("NewYork", 5000);
            serverImpl.addRemoteServer("London", 5001);

            // Start UDP server thread
            UDPServerThread udpThread = new UDPServerThread(
                    5002,
                    serverImpl,
                    serverImpl.getShareDatabase()
            );
            udpThread.start();

            // Wait for invocations from clients
            orb.run();
        } catch (Exception e) {
            System.err.println("ERROR: " + e);
            e.printStackTrace(System.out);
        }
    }
}