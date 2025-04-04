package client;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

public class AdminClient {
    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your Admin ID (e.g., NYKAXXXX, LONAXXXX, TOKAXXXX): ");
            String adminID = scanner.next();

            // Determine correct server location
            String cityCode = adminID.substring(0, 3);
            String serverName = getFullCityName(cityCode);

            if (serverName == null || adminID.length()<=7) {
                System.out.println("Invalid Admin ID. Exiting.");
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

            System.out.println("Connected to " + cityCode + " Server.");

            while (true) {
                System.out.println("\nAdmin Menu (" + cityCode + ")");
                System.out.println("1. Add Share");
                System.out.println("2. Remove Share");
                System.out.println("3. List Share Availability");
                System.out.println("4. Purchase Share (Buyer Function)");
                System.out.println("5. View My Shares (Buyer Function)");
                System.out.println("6. Sell Share (Buyer Function)");
                System.out.println("7. Exit");
                System.out.print("Enter your choice: ");
                int choice = scanner.nextInt();

                String response;
                switch (choice) {
                    case 1:
                        System.out.println("Example Share ID: LOCTDDMMYY (LOC: New York/T:Time/DDMMYY)");
                        System.out.print("Enter Share ID: ");
                        String shareID = scanner.next();
                        System.out.print("Enter Share Type (Equity/Bonus/Dividend): ");
                        String shareType = scanner.next();
                        System.out.print("Enter Capacity: ");
                        int capacity = scanner.nextInt();
                        response = server.addShare(shareID, shareType, capacity);
                        logAction(adminID, "addShare", response);
                        System.out.println(response);
                        break;
                    case 2:
                        System.out.print("Enter Share ID to remove: ");
                        shareID = scanner.next();
                        System.out.print("Enter Share Type: ");
                        shareType = scanner.next();
                        response = server.removeShare(shareID, shareType);
                        logAction(adminID, "removeShare", response);
                        System.out.println(response);
                        break;
                    case 3:
                        System.out.print("Enter Share Type to list availability: ");
                        shareType = scanner.next();
                        response = server.listShareAvailability(shareType);
                        logAction(adminID, "listShareAvailability", response);
                        System.out.println(response);
                        break;
                    case 4: // Buy Shares
                        System.out.print("Enter Share ID: ");
                        shareID = scanner.next();
                        System.out.print("Enter Share Type (Equity/Bonus/Dividend): ");
                        shareType = scanner.next();
                        System.out.print("Enter Quantity: ");
                        int quantity = scanner.nextInt();
                        response = server.purchaseShare(adminID, shareID, shareType, quantity);
                        logAction(adminID, "purchaseShare", response);
                        System.out.println(response);
                        break;
                    case 5: // View Shares
                        response = server.getShares(adminID);
                        logAction(adminID, "getShares", response);
                        System.out.println("Your Shares: " + response);
                        break;
                    case 6: // Sell Shares
                        System.out.print("Enter Share ID to sell: ");
                        shareID = scanner.next();
                        System.out.print("Enter Quantity: ");
                        quantity = scanner.nextInt();
                        response = server.sellShare(adminID, shareID, quantity);
                        logAction(adminID, "sellShare", response);
                        System.out.println(response);
                        break;
                    case 7:
                        System.out.println("Exiting Admin System.");
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

    private static String getFullCityName(String cityCode) {
        switch (cityCode) {
            case "NYK": return "NewYork";
            case "LON": return "London";
            case "TOK": return "Tokyo";
            default: return null;
        }
    }

    private static void logAction(String userID, String action, String response) {
        try {
            FileWriter writer = new FileWriter("logs/Admin_" + userID + ".log", true);
            writer.write(action + " → " + response + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}