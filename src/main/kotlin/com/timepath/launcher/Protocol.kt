package com.timepath.launcher

import java.rmi.Remote
import java.rmi.RemoteException

public interface Protocol : Remote {

    throws(RemoteException::class)
    public fun newFrame()
}
