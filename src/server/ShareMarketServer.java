package server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ShareMarketServer extends Remote {
    // Admin Operations
    String addShare(String shareID, String shareType, int capacity) throws RemoteException;
    String removeShare(String shareID, String shareType) throws RemoteException;
    String listShareAvailability(String shareType) throws RemoteException;

    String purchaseRemoteShare(String buyerID, String shareID, String shareType, 
    int shareCount, String targetMarket) throws RemoteException;
    String sellRemoteShare(String buyerID, String shareID, String shareType, 
    int shareCount, String targetMarket) throws RemoteException;

    // Buyer Operations
    String purchaseShare(String buyerID, String shareID, String shareType, int shareCount) throws RemoteException;
    String getShares(String buyerID) throws RemoteException;
    String sellShare(String buyerID, String shareID, int shareCount) throws RemoteException;

    String swapShares(String buyerID, String oldShareID, String oldShareType, String newShareID, String newShareType) throws RemoteException;

}
