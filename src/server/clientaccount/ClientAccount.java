package server.clientaccount;

import server.clientaccount.wallet.Wallet;

import java.util.HashMap;

public class ClientAccount {

    private final String username;
    private boolean isLoggedIn;
    private final Wallet wallet;

    public ClientAccount(String username) {
        this(username, 0);
    }

    public ClientAccount(String username, double walletBalance) {
        this.username = username;
        this.wallet = new Wallet(walletBalance);
        this.isLoggedIn = false;
    }

    public ClientAccount(String username, double walletBalance, String assets) {
        this(username, walletBalance);
        this.wallet.setAssets(assets); // TODO: to transform the string ASSETS to hash map
    }

    public String getUsername() {
        return username;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void setLoggedIn(boolean isLoggedIn) {
        this.isLoggedIn = isLoggedIn;
    }

    public double getWalletBalance() {
        return wallet.getBalance();
    }

    public void depositMoney(double amount) {
        wallet.depositMoney(amount);
    }

    public boolean buyAsset(String assetID, double amount, double price) {
        return wallet.buyAsset(assetID, amount, price);
    }

    public boolean sellAsset(String assetID, double price) {
        return wallet.sellAsset(assetID, price);
    }

    public String getAssets() {
        return wallet.getAssets();
    }
}
