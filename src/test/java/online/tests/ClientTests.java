package online.tests;

import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.*;
import com.google.gson.*;

public final class ClientTests {
    public static void run() throws Exception {
        try{Class.forName("online.net.RoomClient");}catch(ClassNotFoundException e){throw new AssertionError("Missing desktop socket client",e);}
    }
}
