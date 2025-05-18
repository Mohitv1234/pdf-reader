package com.example.pdfviewer;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javafx.embed.swing.SwingFXUtils;

public class PDFViewerApp extends Application {
    private double zoomLevel = 1.0;
    private static final double ZOOM_FACTOR = 1.5; // For double-click and buttons
    private static final double SCROLL_ZOOM_FACTOR = 1.2; // Smoother for scroll wheel
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 10.0;
    private ImageView pdfView;
    private Pane pane;
    private PDDocument document;
    private PDFRenderer pdfRenderer;
    private Rectangle selectionRect;
    private double startX, startY;
    private boolean isSelecting = false;
    private ScrollPane scrollPane;
    private Label statusLabel;
    private int currentPage = 0;
    private File currentFile;

    @Override
    public void start(Stage primaryStage) {
        // Initialize components
        pane = new Pane();
        pdfView = new ImageView();
        pane.getChildren().add(pdfView);
        scrollPane = new ScrollPane(pane);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // File chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        // MenuBar
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("_File");
        Menu viewMenu = new Menu("_View");
        MenuItem openItem = new MenuItem("Open File");
        MenuItem copyAllItem = new MenuItem("Copy All Content");
        MenuItem zoomInItem = new MenuItem("Zoom In");
        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        MenuItem zoomResetItem = new MenuItem("Reset Zoom");
        fileMenu.getItems().addAll(openItem, copyAllItem);
        viewMenu.getItems().addAll(zoomInItem, zoomOutItem, zoomResetItem);
        menuBar.getMenus().addAll(fileMenu, viewMenu);
        menuBar.setStyle("-fx-background-color: #e0e0e0;");

        // ToolBar
        ToolBar toolBar = new ToolBar();
        Button openButton = createToolbarButton("Open", "/open.png");
        Button zoomInButton = createToolbarButton("Zoom In", "/zoom-in.png");
        Button zoomOutButton = createToolbarButton("Zoom Out", "/zoom-out.png");
        Slider zoomSlider = new Slider(10, 1000, 100); // 10% to 1000%
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setMajorTickUnit(100);
        zoomSlider.setMinorTickCount(25);
        Label zoomLabel = new Label("Zoom:");
        Button prevPageButton = createToolbarButton("Previous Page", "/prev.png");
        Button nextPageButton = createToolbarButton("Next Page", "/next.png");
        toolBar.getItems().addAll(
                openButton, new Separator(),
                prevPageButton, nextPageButton, new Separator(),
                zoomInButton, zoomOutButton, zoomLabel, zoomSlider
        );
        toolBar.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");

        // Status Bar
        statusLabel = new Label("No PDF loaded");
        BorderPane statusBar = new BorderPane();
        statusBar.setLeft(statusLabel);
        statusBar.setPadding(new Insets(2, 5, 2, 5));
        statusBar.setStyle("-fx-background-color: #e0e0e0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        // Text selection handling
        selectionRect = new Rectangle();
        selectionRect.setFill(Color.YELLOW.deriveColor(0, 1, 1, 0.3)); // Yellow highlight
        selectionRect.setStroke(Color.YELLOW.darker());
        selectionRect.setVisible(false);
        pane.getChildren().add(selectionRect);

        // Layout
        VBox root = new VBox(menuBar, toolBar, scrollPane, statusBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        // Open File action
        Runnable openFileAction = () -> {
            File file = fileChooser.showOpenDialog(primaryStage);
            if (file != null) {
                try {
                    statusLabel.setText("Loading " + file.getName() + "...");
                    if (document != null) {
                        document.close();
                    }
                    document = PDDocument.load(file);
                    pdfRenderer = new PDFRenderer(document);
                    currentFile = file;
                    currentPage = 0;
                    zoomLevel = 1.0;
                    zoomSlider.setValue(100);
                    renderPage(currentPage);
                    updateStatus();
                } catch (IOException e) {
                    showErrorDialog("Failed to load PDF: " + e.getMessage());
                    statusLabel.setText("Error loading PDF");
                }
            }
        };
        openItem.setOnAction(e -> openFileAction.run());
        openButton.setOnAction(e -> openFileAction.run());
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));

        // Zoom actions
        Runnable zoomInAction = () -> {
            zoomLevel = Math.min(zoomLevel * ZOOM_FACTOR, MAX_ZOOM);
            zoomSlider.setValue(zoomLevel * 100);
            renderPage(currentPage);
            scrollPane.setHvalue(0.5);
            scrollPane.setVvalue(0.5);
            updateStatus();
        };
        Runnable zoomOutAction = () -> {
            zoomLevel = Math.max(zoomLevel / ZOOM_FACTOR, MIN_ZOOM);
            zoomSlider.setValue(zoomLevel * 100);
            renderPage(currentPage);
            scrollPane.setHvalue(0.5);
            scrollPane.setVvalue(0.5);
            updateStatus();
        };
        zoomInItem.setOnAction(e -> zoomInAction.run());
        zoomOutItem.setOnAction(e -> zoomOutAction.run());
        zoomInButton.setOnAction(e -> zoomInAction.run());
        zoomOutButton.setOnAction(e -> zoomOutAction.run());
        zoomResetItem.setOnAction(e -> {
            zoomLevel = 1.0;
            zoomSlider.setValue(100);
            renderPage(currentPage);
            scrollPane.setHvalue(0.5);
            scrollPane.setVvalue(0.5);
            updateStatus();
        });
        zoomInItem.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN));
        zoomOutItem.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
        zoomResetItem.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN));

        // Zoom slider
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            zoomLevel = newVal.doubleValue() / 100;
            renderPage(currentPage);
            scrollPane.setHvalue(0.5);
            scrollPane.setVvalue(0.5);
            updateStatus();
        });

        // Page navigation
        prevPageButton.setOnAction(e -> {
            if (currentPage > 0) {
                currentPage--;
                renderPage(currentPage);
                updateStatus();
            }
        });
        nextPageButton.setOnAction(e -> {
            if (document != null && currentPage < document.getNumberOfPages() - 1) {
                currentPage++;
                renderPage(currentPage);
                updateStatus();
            }
        });

        // Copy All Content action
        Runnable copyAllAction = () -> {
            if (document != null) {
                try {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(currentPage + 1);
                    stripper.setEndPage(currentPage + 1);
                    String allText = stripper.getText(document).trim();
                    if (!allText.isEmpty()) {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(allText);
                        clipboard.setContent(content);
                        showInfoDialog("All text copied to clipboard");
                    } else {
                        showInfoDialog("No text found on this page");
                    }
                } catch (IOException e) {
                    showErrorDialog("Failed to copy text: " + e.getMessage());
                }
            }
        };
        copyAllItem.setOnAction(e -> copyAllAction.run());
        copyAllItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));

        // Zoom handling (double-click)
        pdfView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                double mouseX = event.getX();
                double mouseY = event.getY();
                double pivotX = mouseX / pdfView.getFitWidth();
                double pivotY = mouseY / pdfView.getFitHeight();

                if (event.getButton() == MouseButton.PRIMARY) {
                    zoomLevel = Math.min(zoomLevel * ZOOM_FACTOR, MAX_ZOOM);
                } else if (event.getButton() == MouseButton.SECONDARY) {
                    zoomLevel = Math.max(zoomLevel / ZOOM_FACTOR, MIN_ZOOM);
                }
                zoomSlider.setValue(zoomLevel * 100);
                renderPage(currentPage);
                scrollPane.setHvalue(pivotX * (pane.getWidth() - scrollPane.getViewportBounds().getWidth()) / scrollPane.getViewportBounds().getWidth());
                scrollPane.setVvalue(pivotY * (pane.getHeight() - scrollPane.getViewportBounds().getHeight()) / scrollPane.getViewportBounds().getHeight());
                updateStatus();
            }
        });

        // Zoom handling (scroll wheel)
        pdfView.setOnScroll(event -> {
            double mouseX = event.getX();
            double mouseY = event.getY();
            double pivotX = mouseX / pdfView.getFitWidth();
            double pivotY = mouseY / pdfView.getFitHeight();

            if (event.getDeltaY() > 0) {
                zoomLevel = Math.min(zoomLevel * SCROLL_ZOOM_FACTOR, MAX_ZOOM);
            } else if (event.getDeltaY() < 0) {
                zoomLevel = Math.max(zoomLevel / SCROLL_ZOOM_FACTOR, MIN_ZOOM);
            }
            zoomSlider.setValue(zoomLevel * 100);
            renderPage(currentPage);
            scrollPane.setHvalue(pivotX * (pane.getWidth() - scrollPane.getViewportBounds().getWidth()) / scrollPane.getViewportBounds().getWidth());
            scrollPane.setVvalue(pivotY * (pane.getHeight() - scrollPane.getViewportBounds().getHeight()) / scrollPane.getViewportBounds().getHeight());
            updateStatus();
            event.consume();
        });

        // Text selection handling
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
                        showInfoDialog("Selected text copied to clipboard");
                    }
                }
                selectionRect.setVisible(false);
            }
        });

        // Initial file open
        openFileAction.run();

        // Set up scene
        primaryStage.setTitle("PDF Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Button createToolbarButton(String tooltip, String iconPath) {
        Button button = new Button();
        try {
            Image icon = new Image(getClass().getResourceAsStream(iconPath));
            button.setGraphic(new ImageView(icon));
        } catch (Exception e) {
            button.setText(tooltip); // Fallback to text if icon fails
        }
        button.setTooltip(new Tooltip(tooltip));
        button.setStyle("-fx-background-color: transparent; -fx-padding: 5;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #d0d0d0; -fx-padding: 5;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-padding: 5;"));
        return button;
    }

    private void renderPage(int pageNum) {
        try {
            float dpi = Math.min((float) (72 * zoomLevel), 300f);
            BufferedImage image = pdfRenderer.renderImageWithDPI(pageNum, dpi);
            pdfView.setImage(SwingFXUtils.toFXImage(image, null));
            pdfView.setFitWidth(image.getWidth());
            pdfView.setFitHeight(image.getHeight());
            pane.setPrefSize(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            showErrorDialog("Failed to render page: " + e.getMessage());
        }
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
            stripper.extractRegions(document.getPage(currentPage));
            return stripper.getTextForRegion("selection").trim();
        } catch (IOException e) {
            showErrorDialog("Failed to extract text: " + e.getMessage());
            return "";
        }
    }

    private void updateStatus() {
        if (document != null && currentFile != null) {
            statusLabel.setText(String.format("%s | Page %d of %d | Zoom: %.0f%%",
                    currentFile.getName(), currentPage + 1, document.getNumberOfPages(), zoomLevel * 100));
        } else {
            statusLabel.setText("No PDF loaded");
        }
    }

    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfoDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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