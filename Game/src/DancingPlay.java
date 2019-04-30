import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class DancingPlay extends JPanel {
    private long timeCount = 0;
    private int square = DanceDirection.FRAME_LENGTH + 6;
    private boolean niceFlag = false;
    private int dispointNiceString = 10;
    private Queue<DanceDirection> queue = new LinkedList<DanceDirection>();
    private Server server;
    private static Lock lock = new ReentrantLock();
    private int combo = 0;
    private Lock combolock = new ReentrantLock();
    private boolean initialFlag = true;

    DancingPlay(Server server){
        super();
        this.server = server;
    }

    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);
        drawDerction(graphics);
        drawSquare(graphics);
        drawNice(graphics);
    }

    private void drawDerction(Graphics graphics) {
        lock.lock();
        Iterator<DanceDirection> iterator = queue.iterator();
        while (iterator.hasNext()) {
            iterator.next().drawImage(graphics);
        }
        lock.unlock();
    }

    private void drawNice(Graphics graphics) {
        if (niceFlag) {
            graphics.setColor(Color.red);
            Font font = graphics.getFont();
            Font font2 = new Font(font.getName(), font.getStyle(), font.getSize() + 50);
            graphics.setFont(font2);
            graphics.drawString("Nice", 1024 / 2 - square - 280, 600);
            graphics.drawString("Combo *"+combo, 1024 / 2 + square, 600);
        }
    }

    private void drawSquare(Graphics graphics) {
        graphics.setColor(Color.black);
        for (int i = 0; i < 6; i += 2) {
            graphics.drawRoundRect(1024 / 2 - (square + i) / 2, 500 - i / 2, square + i, square + i, 5, 5);
        }
    }

    public static void main(String[] args) {
        Server server = new Server(8989);
        server.startReader();

        JFrame jFrame = new JFrame();
        jFrame.setSize(1024, 768);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setLocation((Toolkit.getDefaultToolkit().getScreenSize().width - 1024) / 2,
                (Toolkit.getDefaultToolkit().getScreenSize().height - 768) / 2);
        DancingPlay dancingPlay = new DancingPlay(server);
        jFrame.add(dancingPlay);
        dancingPlay.setFocusable(true);
        dancingPlay.run();
        jFrame.setVisible(true);

        WavPlayer player = new WavPlayer("./resources/Godknows.wav");
        player.play();
        jFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                server.startWriter("bye");
            }
        });
    }

    public void checkKey(int keyNumber) {
        lock.lock();
        Iterator<DanceDirection> iterator = queue.iterator();
        while (iterator.hasNext()) {
            DanceDirection danceDirection = iterator.next();
            if (Math.abs(danceDirection.positionY - 500) <= 30
                    && danceDirection.direction == keyNumber) {
                niceFlag = true;
                if (!danceDirection.hit) {
                    combolock.lock();
                    combo ++;
                    combolock.unlock();
                    danceDirection.hit = true;
                }
                switch (keyNumber) {
                    case 1: server.startWriter("Right"); break;
                    case 2: server.startWriter("Left"); break;
                }
            }
        }
        lock.unlock();
    }

    public void run() {

        Thread socketKeyThread = new Thread() {
            public void run() {
                while (true) {
                    float leftProb = server.leftProb();
                    float rightProb = server.rightProb();
                    //System.out.println("I/"+leftProb+" "+rightProb);
                    if (rightProb > leftProb){
                        if (rightProb>0.8) {
                            checkKey(1);
                        }
                    } else {
                        if (leftProb>0.8) {
                            checkKey(2);
                        }
                    }
                    try {
                        Thread.sleep(2);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        };
        socketKeyThread.start();

        Thread thread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        Iterator<DanceDirection> iterator = queue.iterator();
                        while (iterator.hasNext()) {
                            DanceDirection danceDirection = iterator.next();
                            if (danceDirection.positionY >= 768) {
                                lock.lock();
                                iterator.remove();
                                lock.unlock();
                                combolock.lock();
                                if (!danceDirection.hit) combo = 0;
                                combolock.unlock();
                            }
                            danceDirection.positionY += 6;
                        }

                        if (niceFlag) {
                            dispointNiceString++;
                            if (dispointNiceString >= 10) {
                                niceFlag = false;
                                dispointNiceString = 0;
                            }
                        }

                        if (timeCount++ % 25 == 0) {
                            if (Math.random() * 4 > 1) {
                                queue.offer(DanceDirection.getInstance());
                            }
                        }
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    repaint();
                }
            }
        };
        thread.start();
    }
}

class DanceDirection {

    static final int FRAME_LENGTH = 50;
    static final int RIGHT = 1;
    static final int LEFT = 2;
    static final int UP = 3;
    static final int DOWN = 4;
    int positionX = 1024 / 2;
    int positionY = 0 - FRAME_LENGTH;
    int direction;
    boolean hit = false;

    private DanceDirection() {
    }

    public static DanceDirection getInstance() {
        return new Dance();
    }

    static class Dance extends DanceDirection {
        public Dance() {
            direction = (int) (Math.random() * 2 + 1);
        }
    }

    public void drawImage(Graphics graphics) {
        switch (direction) {
            case RIGHT:
                rightDirection(graphics);
                break;
            case LEFT:
                leftDirection(graphics);
                break;
        }
    }

    private void leftDirection(Graphics graphics) {
        graphics.setColor(Color.magenta);
        for (int i = 0; i <= 2; i++) {
            int[] arrayX = {positionX - FRAME_LENGTH / 2 + 2 + 15 * i,
                    positionX - FRAME_LENGTH / 2 + 2 + 15 + 15 * i,
                    positionX - FRAME_LENGTH / 2 + 2 + 15 + 15 * i};
            int[] arrayY = {positionY + FRAME_LENGTH / 2,
                    positionY + FRAME_LENGTH / 2 - 20,
                    positionY + FRAME_LENGTH / 2 + 20};
            graphics.fillPolygon(arrayX, arrayY, arrayX.length);
        }
    }

    private void rightDirection(Graphics graphics) {
        graphics.setColor(Color.orange);
        for (int i = 0; i <= 2; i++) {
            int[] arrayX = {positionX + FRAME_LENGTH / 2 - 2 - 15 * i,
                    positionX + FRAME_LENGTH / 2 - 2 - 15 - 15 * i,
                    positionX + FRAME_LENGTH / 2 - 2 - 15 - 15 * i};
            int[] arrayY = {positionY + FRAME_LENGTH / 2,
                    positionY + FRAME_LENGTH / 2 + 20,
                    positionY + FRAME_LENGTH / 2 - 20};
            graphics.fillPolygon(arrayX, arrayY, arrayX.length);
        }
    }

    @Override
    public String toString() {
        return "direciton:" + direction + "\tpositionX:" + positionX + "\tpositionY:" + positionY;
    }
}


class Server{
    private ServerSocket server;
    private BufferedReader reader;
    private BufferedWriter writer;
    private ExecutorService mThreadPool;
    private static Lock writeLock = new ReentrantLock();
    private static Lock readLock = new ReentrantLock();
    private float leftprobability;
    private float rightprobabitlity;

    Server(int port) {
        try {
            mThreadPool = Executors.newCachedThreadPool();
            server = new ServerSocket(port);
            System.out.println("Waiting for connection...");
            Socket socket = server.accept();
            System.out.println("IP: " + socket.getInetAddress().getHostAddress());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            System.out.println("Connected");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void startReader(){
        mThreadPool.execute(new ReaderThread());
    }

    void startWriter(String toSend){
        mThreadPool.execute(new WriterThread(toSend));
    }

    class ReaderThread implements Runnable {
        @Override
        public void run(){
            readLock.lock();
            try {
                String lineString;
                while (!(lineString = reader.readLine()).equals("bye")) {
                    String[] intArray = lineString.split(" ");
                    leftprobability = Float.parseFloat(intArray[0]);
                    rightprobabitlity = Float.parseFloat(intArray[1]);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            readLock.unlock();
        }
    }

    class WriterThread implements Runnable {
        String toSend;
        WriterThread(String toSend){
            super();
            this.toSend = toSend;
        }
        @Override
        public void run(){
            writeLock.lock();
            try {
                writer.write(toSend + "\n");
                writer.flush();
            } catch (IOException e){
                e.printStackTrace();
            }
            writeLock.unlock();
        }
    }

     float leftProb(){
        return leftprobability;
     }
     float rightProb(){
        return rightprobabitlity;
     }
}