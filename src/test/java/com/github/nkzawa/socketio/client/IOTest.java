package com.github.nkzawa.socketio.client;

import com.github.nkzawa.emitter.Emitter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class IOTest {

    final static int TIMEOUT = 3000;
    final static int PORT = 3000;

    private Process serverProcess;
    private ExecutorService serverService;
    private Future serverOutout;
    private Future serverError;
    private Socket socket;

    @Before
    public void startServer() throws IOException, InterruptedException {
        System.out.println("Starting server ...");

        final CountDownLatch latch = new CountDownLatch(1);
        serverProcess = Runtime.getRuntime().exec(
                "node src/test/resources/index.js " + PORT, new String[] {"DEBUG=socket.io:*,engine*"});
        serverService = Executors.newCachedThreadPool();
        serverOutout = serverService.submit(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getInputStream()));
                String line;
                try {
                    line = reader.readLine();
                    latch.countDown();
                    do {
                        System.out.println("SERVER OUT: " + line);
                    } while ((line = reader.readLine()) != null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        serverError = serverService.submit(new Runnable() {
            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getErrorStream()));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        System.err.println("SERVER ERR: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        latch.await(3000, TimeUnit.MILLISECONDS);
    }

    @After
    public void stopServer() throws InterruptedException {
        System.out.println("Stopping server ...");
        serverProcess.destroy();
        serverOutout.cancel(false);
        serverError.cancel(false);
        serverService.shutdown();
        serverService.awaitTermination(3000, TimeUnit.MILLISECONDS);
    }

    @Test(timeout = TIMEOUT)
    public void openAndClose() throws URISyntaxException, InterruptedException {
        final BlockingQueue<String> events = new LinkedBlockingQueue<String>();

        IO.Options opts = new IO.Options();
        opts.forceNew = true;
        socket = IO.socket("http://localhost:" + PORT, opts);
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                System.out.println("connect:");
                events.offer("connect");
            }
        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                System.out.println("disconnect:");
                events.offer("disconnect");
            }
        });
        socket.connect();

        assertThat(events.take(), is("connect"));
        socket.disconnect();
        assertThat(events.take(), is("disconnect"));
    }

    @Test(timeout = TIMEOUT)
    public void message() throws URISyntaxException, InterruptedException {
        final BlockingQueue<String> events = new LinkedBlockingQueue<String>();

        IO.Options opts = new IO.Options();
        opts.forceNew = true;
        socket = IO.socket("http://localhost:" + PORT, opts);
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                System.out.println("connect:");
                socket.send("hi");
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
            @Override
            public void call(Object... objects) {
                System.out.println("message: " + objects);
                events.offer((String) objects[0]);
            }
        });
        socket.connect();

        assertThat(events.take(), is("hello client"));
        assertThat(events.take(), is("hi"));
        socket.disconnect();
    }
}