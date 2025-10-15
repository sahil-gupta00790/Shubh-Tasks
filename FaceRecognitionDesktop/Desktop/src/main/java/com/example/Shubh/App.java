package com.example.Shubh;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class App {
    private JFrame frame;
    private JLabel imageLabel;
    private JLabel statusLabel;
    private VideoCapture camera;
    private CascadeClassifier faceDetector;
    private ScheduledExecutorService executor;
    private String currentRequestId = null;
    private boolean waitingForApproval = false;

    public static void main(String[] args) {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        SwingUtilities.invokeLater(() -> new App().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Face Recognition System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        imageLabel = new JLabel();
        imageLabel.setPreferredSize(new Dimension(640, 480));
        imageLabel.setBorder(BorderFactory.createTitledBorder("Camera Feed"));

        statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setPreferredSize(new Dimension(640, 50));

        JButton startButton = new JButton("Start Detection");
        JButton stopButton = new JButton("Stop Detection");

        startButton.addActionListener(e -> startDetection());
        stopButton.addActionListener(e -> stopDetection());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        frame.add(imageLabel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);
        frame.add(buttonPanel, BorderLayout.NORTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        initializeOpenCV();
    }

    private void initializeOpenCV() {
        try {
            String cascadePath = "src/main/resources/haarcascade_frontalface_alt.xml";
            faceDetector = new CascadeClassifier(cascadePath);

            if (faceDetector.empty()) {
                statusLabel.setText("Error: Could not load face detector");
                return;
            }

            statusLabel.setText("Ready to start detection");
        } catch (Exception e) {
            statusLabel.setText("Error initializing OpenCV: " + e.getMessage());
        }
    }


    private void startDetection() {
        if (camera != null && camera.isOpened()) {
            camera.release();
        }

        camera = new VideoCapture(0);

        if (!camera.isOpened()) {
            statusLabel.setText("Error: Could not open camera");
            return;
        }

        statusLabel.setText("Scanning for faces...");

        executor = Executors.newScheduledThreadPool(2);
        executor.scheduleAtFixedRate(this::captureAndDetect, 0, 100, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::checkApprovalStatus, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void stopDetection() {
        if (executor != null) {
            executor.shutdown();
        }
        if (camera != null && camera.isOpened()) {
            camera.release();
        }
        statusLabel.setText("Detection stopped");
        waitingForApproval = false;
        currentRequestId = null;
    }

    private void captureAndDetect() {
        if (waitingForApproval) return;

        Mat frame = new Mat();
        if (!camera.read(frame) || frame.empty()) return;

        Mat grayFrame = new Mat();
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);

        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(grayFrame, faces, 1.1, 3, 0, new Size(30, 30), new Size());

        Rect[] faceArray = faces.toArray();

        for (Rect face : faceArray) {
            Imgproc.rectangle(frame, new org.opencv.core.Point(face.x, face.y),
                    new org.opencv.core.Point(face.x + face.width, face.y + face.height),
                    new Scalar(0, 255, 0), 2);
        }

        displayImage(frame);

        if (faceArray.length > 0 && !waitingForApproval) {
            String faceHash = calculateFaceHash(grayFrame, faceArray[0]);
            String owner = findOwnerByFace(faceHash);

            if (owner != null) {
                sendApprovalRequest(frame, owner);
            }
        }
    }

    private void displayImage(Mat mat) {
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, matOfByte);
        byte[] byteArray = matOfByte.toArray();

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
            BufferedImage bufferedImage = javax.imageio.ImageIO.read(bis);
            ImageIcon imageIcon = new ImageIcon(bufferedImage);
            SwingUtilities.invokeLater(() -> imageLabel.setIcon(imageIcon));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String calculateFaceHash(Mat grayFrame, Rect face) {
        Mat faceROI = new Mat(grayFrame, face);
        Mat resized = new Mat();
        Imgproc.resize(faceROI, resized, new Size(100, 100));

        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".jpg", resized, matOfByte);
        byte[] faceBytes = matOfByte.toArray();

        return Integer.toString(java.util.Arrays.hashCode(faceBytes));
    }

//    private String findOwnerByFace(String faceHash) {
//        try {
//            InputStream is = getClass().getClassLoader().getResourceAsStream("faces.txt");
//            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
//            String line;
//
//            while ((line = reader.readLine()) != null) {
//                String[] parts = line.split(",");
//                if (parts.length >= 2 && parts[1].equals(faceHash)) {
//                    return parts[0];
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
private String findOwnerByFace(String faceHash) {
    System.out.println("Calculated face hash: " + faceHash);
    return "TestUser";  // Always return this for testing
}


    private void sendApprovalRequest(Mat frame, String owner) {
        waitingForApproval = true;
        statusLabel.setText("Face detected! Waiting for " + owner + "'s approval...");

        try {
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".jpg", frame, matOfByte);
            byte[] imageBytes = matOfByte.toArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            currentRequestId = System.currentTimeMillis() + "_" + owner;

            String jsonPayload = "{\"owner\":\"" + owner + "\",\"image\":\"" + base64Image + "\",\"requestId\":\"" + currentRequestId + "\"}";

            URL url = new URL("http://localhost:8081/approval-request");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes());
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                statusLabel.setText("Error sending approval request");
                waitingForApproval = false;
            }

        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            waitingForApproval = false;
        }
    }

    private void checkApprovalStatus() {
        if (!waitingForApproval || currentRequestId == null) return;

        try {
            URL url = new URL("http://localhost:8081/approval-status/" + currentRequestId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = reader.readLine();

                if ("approved".equals(response)) {
                    statusLabel.setText("ACCESS GRANTED!");
                    statusLabel.setForeground(Color.GREEN);
                } else if ("denied".equals(response)) {
                    statusLabel.setText("ACCESS DENIED!");
                    statusLabel.setForeground(Color.RED);
                }

                if (!"pending".equals(response)) {
                    waitingForApproval = false;
                    currentRequestId = null;

                    Timer timer = new Timer(3000, e -> {
                        statusLabel.setText("Scanning for faces...");
                        statusLabel.setForeground(Color.BLACK);
                    });
                    timer.setRepeats(false);
                    timer.start();
                }
            }
        } catch (Exception e) {

        }
    }
}
