package com.timepath.launcher

import java.rmi.Remote
import java.rmi.RemoteException

/**
 * @author TimePath
 */
public trait Protocol : Remote {

    throws(javaClass<RemoteException>())
    public fun newFrame()
}
