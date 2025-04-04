package client;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;


public class BuyerClient {
    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your Buyer ID (e.g., NYKBXXXX, LONBXXXX, TOKBXXXX): ");
            String buyerID = scanner.next();

            // Determine correct server location
            String serverName = ClientMap.getLocation(buyerID);

            if (serverName == null) {
                System.out.println("Invalid Buyer ID. Exiting.");
                return;
            }

            // Initialize the ORB
            ORB orb = ORB.init(args, null);

            // Get reference to the Naming service
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            // Resolve the object reference in the Naming service
            String name = serverName + "ShareMarketServer";
            ShareMarket.Server server = ShareMarket.ServerHelper.narrow(ncRef.resolve_str(name));

            System.out.println("Connected to " + serverName + " Server.");

            while (true) {
                System.out.println("\nBuyer Menu (" + serverName + ")");
                System.out.println("1. Purchase Share (Local Market)");
                System.out.println("2. Purchase Share (Cross-Market)");
                System.out.println("3. View My Shares");
                System.out.println("4. Sell Share (Local Market)");
                System.out.println("5. Sell Share (Cross-Market)");
                System.out.println("6. Swap Shares");
                System.out.println("7. Exit");
                System.out.print("Enter your choice: ");
                int choice = scanner.nextInt();

                String response;
                String firstID;
                switch (choice) {
                    case 1:
                        System.out.println("Example Share ID: LOCTDDMMYY (LOC: New York/T:Time/DDMMYY)");
                        System.out.print("Enter Share ID: ");
                        String shareID = scanner.next();
                        System.out.print("Enter Share Type (Equity/Bonus/Dividend): ");
                        String shareType = scanner.next();
                        System.out.print("Enter Quantity: ");
                        int quantity = scanner.nextInt();
                        response = server.purchaseShare(buyerID, shareID, shareType, quantity);
                        logAction(buyerID, "purchaseShare", response);
                        System.out.println(response);
                        break;

                    case 2: // Cross-Market Purchase
                        System.out.println("Example Share ID: LOCTDDMMYY (LOC: New York/T:Time/DDMMYY)");
                        System.out.print("Enter Share ID: ");
                        shareID = scanner.next();
                        System.out.print("Enter Share Type (Equity/Bonus/Dividend): ");
                        shareType = scanner.next();
                        System.out.print("Enter Quantity: ");
                        quantity = scanner.nextInt();
                        System.out.print("Enter Target Market (NewYork/London/Tokyo): ");
                        String targetMarket = scanner.next();
                        firstID = shareID.substring(0,1);
                        if(firstID != null && firstID.equalsIgnoreCase(targetMarket.substring(0,1))){
                            response = server.purchaseRemoteShare(buyerID, shareID, shareType, quantity, targetMarket);
                            logAction(buyerID, "purchaseRemoteShare", response);
                            System.out.println(response);
                        }
                        else{
                            System.out.println("Purchase failed because Market is not matching with ShareID.");
                        }
                        break;
                    case 3:
                        response = server.getShares(buyerID);
                        logAction(buyerID, "getShares", response);
                        System.out.println(response);
                        break;
                    case 4:
                        System.out.println("Example Share ID: LOCTDDMMYY (LOC: New York/T:Time/DDMMYY)");
                        System.out.print("Enter Share ID to sell: ");
                        shareID = scanner.next();
                        System.out.print("Enter Quantity: ");
                        quantity = scanner.nextInt();
                        response = server.sellShare(buyerID, shareID, quantity);
                        logAction(buyerID, "sellShare", response);
                        System.out.println(response);
                        break;
                    case 5: // Cross-Market Sell
                        System.out.println("Example Share ID: LOCTDDMMYY (LOC: New York/T:Time/DDMMYY)");
                        System.out.print("Enter Share ID to sell: ");
                        shareID = scanner.next();
                        System.out.print("Enter share type: ");
                        shareType = scanner.next();
                        System.out.print("Enter Quantity: ");
                        quantity = scanner.nextInt();
                        System.out.print("Enter Target Market (NewYork/London/Tokyo): ");
                        targetMarket = scanner.next();
                        firstID = shareID.substring(0,1);
                        if(firstID.equalsIgnoreCase(targetMarket.substring(0,1))){
                            response = server.sellRemoteShare(buyerID, shareID, shareType, quantity, targetMarket);
                            logAction(buyerID, "sellRemoteShare", response);
                            System.out.println(response);
                        }
                        else{
                            System.out.println("Sell failed because Market is not matching with ShareID.");
                        }
                        break;
                    case 6: // Swap Operation
                        System.out.println("--- Swap Shares ---");
                        System.out.print("Enter Old Share ID to swap out: ");
                        String oldShareID = scanner.next();
                        System.out.print("Enter Old Share Type (Equity/Bonus/Dividend): ");
                        String oldShareType = scanner.next();
                        System.out.print("Enter New Share ID to swap in: ");
                        String newShareID = scanner.next();
                        System.out.print("Enter New Share Type (Equity/Bonus/Dividend): ");
                        String newShareType = scanner.next();

                        response = server.swapShares(buyerID, oldShareID, oldShareType, newShareID, newShareType);
                        logAction(buyerID, "swapShares", response);
                        System.out.println(response);
                        break;
                    case 7:
                        System.out.println("Exiting Buyer System.");
                        scanner.close();
                        return;
                    default:
                        System.out.println("Invalid choice. Try again.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void logAction(String userID, String action, String response) {
        try {
            FileWriter writer = new FileWriter("logs/Buyer_" + userID + ".log", true);
            writer.write(action + " → " + response + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}