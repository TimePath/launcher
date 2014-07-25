package com.timepath.launcher;

import java.rmi.RemoteException;

/**
 * @author TimePath
 */
public interface Protocol extends java.rmi.Remote {

    void newFrame() throws RemoteException;
}
