// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org
package org.droidmate.uiautomator2daemon;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

import org.droidmate.uiautomator_daemon.SerializationHelper;
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public abstract class Uiautomator2DaemonTcpServerBase<ServerInputT extends Serializable, ServerOutputT extends Serializable>
{
  private int port;
  private ServerSocket serverSocket;

  private String serverStartMessageTag;
  private String serverStartMessage;

  static final String tag = UiautomatorDaemonConstants.uiaDaemon_logcatTag + "/server";

  Uiautomator2DaemonTcpServerBase(String serverStartMessageTag, String serverStartMessage)
  {
    Log.v(tag, "creating base server");
    this.serverStartMessageTag = serverStartMessageTag;
    this.serverStartMessage = serverStartMessage;
  }

  protected abstract ServerOutputT OnServerRequest(ServerInputT input, Exception inputReadEx);

  protected abstract boolean shouldCloseServerSocket(ServerInputT serverInput);

  Thread start(int port) throws InterruptedException
  {
    Log.v(tag, "starting thread");
    this.port = port;
    ServerRunnable serverRunnable = new ServerRunnable();
    Thread serverThread = new Thread(serverRunnable);

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (serverRunnable)
    {
      if (serverSocket != null) throw new AssertionError();
      serverThread.start();
      serverRunnable.wait();
      if (serverSocket == null) throw new AssertionError();
    }

    Log.i(serverStartMessageTag, serverStartMessage);

    return serverThread;

  }

  @TargetApi(Build.VERSION_CODES.FROYO)
  private void close()
  {
    try
    {
      Log.i(tag, "serverSocket.close() of server using "+ port);
      serverSocket.close();
    } catch (IOException e)
    {
      Log.e(tag, "Failed to close droidmate TCP server.");
    }
  }

  // Used in org.droidmate.uiautomatordaemon.UiAutomatorDaemon.init()
  boolean isClosed()
  {
    return serverSocket.isClosed();
  }

  private class ServerRunnable implements Runnable
  {

    // WISH DRY-up Duplicates
    @SuppressWarnings("Duplicates")
    public void run()
    {
      Log.v(tag, "run() using "+port);
      try
      {

        // Synchronize to ensure the parent thread (the one which started this one) will continue only after the
        // serverSocket is initialized.
        synchronized (this)
        {
          Log.d(tag, "new ServerSocket("+port+")");
          serverSocket = new ServerSocket(port);
          this.notify();
        }

        while (!serverSocket.isClosed())
        {
          Log.d(tag, "serverSocket.accept("+port+")");
          Socket clientSocket = serverSocket.accept();

          Log.d(tag, "ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());");
          DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

          /*
           * Flushing done to prevent client blocking on creation of input stream reading output from this stream. See:
           * org.droidmate.device.SerializableTCPClient.queryServer
           *
           * References:
           * 1. http://stackoverflow.com/questions/8088557/getinputstream-blocks
           * 2. Search for: "Note - The ObjectInputStream constructor blocks until" in:
           * http://docs.oracle.com/javase/7/docs/platform/serialization/spec/input.html
           */
//          Log.v(tag, "Output.flush()");
//          output.flush();

          Log.v(tag, "input = new ObjectInputStream(clientSocket.getInputStream());");
          DataInputStream input = new DataInputStream(clientSocket.getInputStream());

          ServerInputT serverInput = null;

          Exception serverInputReadEx = null;

          //noinspection TryWithIdenticalCatches
          try
          {
            Log.v(tag, "input.readObject();");
            @SuppressWarnings("unchecked") // Without this var here, there is no place to put the "unchecked" suppression warning.
              ServerInputT localVarForSuppressionAnnotation = (ServerInputT) SerializationHelper.Companion.readObjectFromStream(input);
            serverInput = localVarForSuppressionAnnotation;

          } catch (ClassNotFoundException e)
          {
            serverInputReadEx = handleInputReadObjectException(input, e);
          } catch (IOException e)
          {
            serverInputReadEx = handleInputReadObjectException(input, e);
          }

          ServerOutputT serverOutput;
          Log.v(tag, "serverOutput = OnServerRequest(serverInput, serverInputReadEx);");
          serverOutput = OnServerRequest(serverInput, serverInputReadEx);
          Log.v(tag, "output.writeObject(serverOutput);");
          SerializationHelper.Companion.writeObjectToStream(output, serverOutput);
          Log.v(tag, "clientSocket.close();");
          clientSocket.close();

          if (shouldCloseServerSocket(serverInput))
            close();
        }

        Log.d(tag, "Closed droidmate TCP server.");

      } catch (SocketTimeoutException e)
      {
        Log.e(tag, "Closing droidmate TCP server due to a timeout.", e);
        close();
      } catch (IOException e)
      {
        Log.e(tag, "Exception was thrown while operating droidmate TCP server", e);
      }
    }

    private Exception handleInputReadObjectException(DataInputStream input, Exception e) throws IOException
    {
      Exception serverInputReadEx;
      Log.e(tag, "Exception was thrown while reading input sent to DroidmateServer from " +
        "client through socket.", e);
      serverInputReadEx = e;
      input.close();
      return serverInputReadEx;
    }
  }

}
