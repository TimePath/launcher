package com.timepath.launcher;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author TimePath
 */
public interface Protocol extends Remote {

    void newFrame() throws RemoteException;
}
