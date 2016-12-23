package com.vilenet.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Tester {

    //static final String hostName = "54.193.49.146";
    static final Random random = new Random(System.currentTimeMillis());
    static final int portNumber = 6112;
//  static final String hostName = "35.166.59.254";
    static final String hostName = "158.69.231.163";
    //static final int portNumber = 7112;

    static AtomicInteger i = new AtomicInteger();

    static class Bot implements Runnable {

        PrintWriter out;
        String name;
        int num;

        Bot(int num, String name) {
            this.num = num;
            this.name = name;
        }

        @Override
        public void run() {
            for (;;){
            try {


                Socket socket = new Socket(hostName, num >= 400 ? 6113 : 6112);//portNumber + random.nextInt(2));
                out =
                        new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));

                out.write(3);
                out.write(4);
                send(name);
                send("1234");
                send("/j vile");
//                System.out.println(i.addAndGet(1));
                Thread.sleep(100 + random.nextInt(300));
                out.close();
                in.close();
                socket.close();
//                String s;
//                while ((s = in.readLine()) != null) {
//                    System.out.println(s);
//                    if (s.contains("$join")) {
//                        send("/j " + s.substring(s.indexOf("$join") + 5));
//                    }
//                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }}
        }

        public void send(String text) {
            out.write(text);
            out.write(0x0D);
            out.write(0x0A);
            out.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        int threads = 199;
        ExecutorService e = Executors.newFixedThreadPool(threads+1);
        for (int i = 1; i <= threads; i++) {
            e.submit(new Bot(i, args[0] + "1"));
        }

        Scanner stdIn = new Scanner(new BufferedReader(new InputStreamReader(System.in)));
        String s;

        while ((s = stdIn.nextLine()) != null) {
            if (s.equals("q")) {
                System.exit(0);
            }
        }
    }
}
