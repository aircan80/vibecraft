package dev.wowforever.app.assets;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveRangeTransportTest {
    @TempDir Path cache;
    @Test void actualHttpClientSendsRangeAndRejectsIgnoredOrMismatchedResponses()throws Exception {
        var mode=new AtomicInteger();var requests=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/archive",exchange->{
            requests.incrementAndGet();assertEquals("bytes=20-29",exchange.getRequestHeaders().getFirst("Range"));
            assertEquals("identity",exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            int status=mode.get()==1?200:206;
            exchange.getResponseHeaders().set("Content-Range",mode.get()==2?"bytes 21-30/100":"bytes 20-29/100");
            byte[] bytes=new byte[mode.get()==1?1024:10];exchange.sendResponseHeaders(status,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
        });server.start();
        try(var client=new NativeAssetClient(cache,BlizzardBuildDescriptor.legacyForever(),NativeAssetClient.TransportPolicy.OFFICIAL_ONLY)) {
            var method=NativeAssetClient.class.getDeclaredMethod("downloadRange",URI.class,long.class,int.class,BooleanSupplier.class);method.setAccessible(true);
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/archive");
            assertEquals(10,((byte[])method.invoke(client,uri,20L,10,(BooleanSupplier)()->false)).length);
            for(int bad:new int[]{1,2}) {
                mode.set(bad);var failure=assertThrows(InvocationTargetException.class,()->method.invoke(client,uri,20L,10,(BooleanSupplier)()->false));
                assertInstanceOf(IOException.class,failure.getCause());
            }
            int before=requests.get();
            var cancelled=assertThrows(InvocationTargetException.class,()->method.invoke(client,uri,20L,10,(BooleanSupplier)()->true));
            assertInstanceOf(java.util.concurrent.CancellationException.class,cancelled.getCause());assertEquals(before,requests.get());
        }finally{server.stop(0);}
    }
}
