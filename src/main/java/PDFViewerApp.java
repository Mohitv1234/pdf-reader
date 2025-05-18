package com.example.pdfviewer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javafx.embed.swing.SwingFXUtils;

public class PDFViewerApp extends Application {
    private double zoomLevel = 1.0;
    private static final double ZOOM_FACTOR = 1.5; // 50% zoom step
    private static final double MIN_ZOOM = 0.1; // 10% of original size
    private static final double MAX_ZOOM = 10.0; // 1000% of original size
    private ImageView pdfView;
    private Pane pane;
    private PDDocument document;
    private PDFRenderer pdfRenderer;
    private Rectangle selectionRect;
    private double startX, startY;
    private boolean isSelecting = false;

    @Override
    public void start(Stage primaryStage) {
        // File chooser to select PDF
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file == null) {
            System.exit(0);
            return;
        }

        // Initialize pane and components
        pane = new Pane();
        pdfView = new ImageView();
        pane.getChildren().add(pdfView);
        ScrollPane scrollPane = new ScrollPane(pane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        // Load PDF
        try {
            document = PDDocument.load(file);
            pdfRenderer = new PDFRenderer(document);
            renderPage(0, zoomLevel); // Render first page
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        // Zoom handling (double-click)
        pdfView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                double mouseX = event.getX();
                double mouseY = event.getY();
                double pivotX = mouseX / pdfView.getFitWidth();
                double pivotY = mouseY / pdfView.getFitHeight();

                if (event.getButton() == MouseButton.PRIMARY) {
                    zoomLevel = Math.min(zoomLevel * ZOOM_FACTOR, MAX_ZOOM); // Zoom in
                } else if (event.getButton() == MouseButton.SECONDARY) {
                    zoomLevel = Math.max(zoomLevel / ZOOM_FACTOR, MIN_ZOOM); // Zoom out
                }

                try {
                    renderPage(0, zoomLevel);
                    // Adjust scroll position to keep mouse pointer in same relative position
                    scrollPane.setHvalue(pivotX * (pane.getWidth() - scrollPane.getWidth()) / scrollPane.getWidth());
                    scrollPane.setVvalue(pivotY * (pane.getHeight() - scrollPane.getHeight()) / scrollPane.getHeight());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Zoom handling (scroll wheel)
        pdfView.setOnScroll(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            double pivotX = mouseX / pdfView.getFitWidth();
            double pivotY = mouseY / pdfView.getFitHeight();

            if (event.getDeltaY() > 0) {
                zoomLevel = Math.min(zoomLevel * ZOOM_FACTOR, MAX_ZOOM); // Scroll up: zoom in
            } else if (event.getDeltaY() < 0) {
                zoomLevel = Math.max(zoomLevel / ZOOM_FACTOR, MIN_ZOOM); // Scroll down: zoom out
            }

            try {
                renderPage(0, zoomLevel);
                // Adjust scroll position to keep mouse pointer in same relative position
                scrollPane.setHvalue(pivotX * (pane.getWidth() - scrollPane.getWidth()) / scrollPane.getWidth());
                scrollPane.setVvalue(pivotY * (pane.getHeight() - scrollPane.getHeight()) / scrollPane.getHeight());
            } catch (IOException e) {
                e.printStackTrace();
            }
            event.consume(); // Prevent default scroll behavior
        });

        // Text selection handling
        selectionRect = new Rectangle();
        selectionRect.setFill(Color.BLUE.deriveColor(0, 1, 1, 0.3));
        selectionRect.setStroke(Color.BLUE);
        selectionRect.setVisible(false);
        pane.getChildren().add(selectionRect);

        pdfView.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                startX = event.getX();
                startY = event.getY();
                isSelecting = true;
                selectionRect.setX(startX);
                selectionRect.setY(startY);
                selectionRect.setWidth(0);
                selectionRect.setHeight(0);
                selectionRect.setVisible(true);
            }
        });

        pdfView.setOnMouseDragged(event -> {
            if (isSelecting) {
                double endX = event.getX();
                double endY = event.getY();
                selectionRect.setX(Math.min(startX, endX));
                selectionRect.setY(Math.min(startY, endY));
                selectionRect.setWidth(Math.abs(endX - startX));
                selectionRect.setHeight(Math.abs(endY - startY));
            }
        });

        pdfView.setOnMouseReleased(event -> {
            if (isSelecting) {
                isSelecting = false;
                if (selectionRect.getWidth() > 5 && selectionRect.getHeight() > 5) {
                    String selectedText = extractTextFromSelection(
                            selectionRect.getX(), selectionRect.getY(),
                            selectionRect.getWidth(), selectionRect.getHeight());
                    if (!selectedText.isEmpty()) {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(selectedText);
                        clipboard.setContent(content);
                    }
                }
                selectionRect.setVisible(false);
            }
        });

        // Set up scene
        Scene scene = new Scene(scrollPane, 800, 600);
        primaryStage.setTitle("PDF Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void renderPage(int pageNum, double zoom) throws IOException {
        // Cap DPI to prevent excessive memory usage at high zoom levels
        float dpi = Math.min((float) (72 * zoom), 300f); // Limit to 300 DPI for performance
        BufferedImage image = pdfRenderer.renderImageWithDPI(pageNum, dpi);
        pdfView.setImage(SwingFXUtils.toFXImage(image, null));
        pdfView.setFitWidth(image.getWidth());
        pdfView.setFitHeight(image.getHeight());
        pane.setPrefSize(image.getWidth(), image.getHeight());
    }

    private String extractTextFromSelection(double x, double y, double width, double height) {
        try {
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.addRegion("selection",
                    new java.awt.Rectangle(
                            (int) (x / zoomLevel),
                            (int) (y / zoomLevel),
                            (int) (width / zoomLevel),
                            (int) (height / zoomLevel)));
            stripper.extractRegions(document.getPage(0));
            return stripper.getTextForRegion("selection").trim();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public void stop() throws Exception {
        if (document != null) {
            document.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}