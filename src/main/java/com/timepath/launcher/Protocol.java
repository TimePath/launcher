package com.timepath.launcher;

public interface Protocol extends java.rmi.Remote {

    void newFrame() throws java.rmi.RemoteException;
}
