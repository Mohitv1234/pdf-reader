import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PDFReader extends JFrame {
    private PDDocument document;
    private PDFRenderer renderer;
    private int currentPage = 0;
    private float zoom = 1.0f;
    private JLabel pageLabel;
    private JScrollPane scrollPane;
    private CustomImageLabel imageLabel;
    private Point mousePosition;
    private Point selectionStart;
    private Point selectionEnd;
    private JButton copyTextButton;
    private Timer longPressTimer;
    private boolean isDragging;
    private long pressStartTime;

    // Custom JLabel to draw selection rectangle
    class CustomImageLabel extends JLabel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (selectionStart != null && selectionEnd != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setColor(new Color(0, 0, 255, 50)); // Semi-transparent blue
                g2d.fillRect(
                        Math.min(selectionStart.x, selectionEnd.x),
                        Math.min(selectionStart.y, selectionEnd.y),
                        Math.abs(selectionEnd.x - selectionStart.x),
                        Math.abs(selectionEnd.y - selectionStart.y)
                );
                g2d.setColor(Color.BLUE);
                g2d.drawRect(
                        Math.min(selectionStart.x, selectionEnd.x),
                        Math.min(selectionStart.y, selectionEnd.y),
                        Math.abs(selectionEnd.x - selectionStart.x),
                        Math.abs(selectionEnd.y - selectionStart.y)
                );
                g2d.dispose();
            }
        }
    }

    public PDFReader() {
        setTitle("PDF Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Create menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open PDF");
        fileMenu.add(openItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // Create toolbar
        JToolBar toolBar = new JToolBar();
        JButton prevButton = new JButton("Previous");
        JButton nextButton = new JButton("Next");
        JButton zoomInButton = new JButton("Zoom In");
        JButton zoomOutButton = new JButton("Zoom Out");
        copyTextButton = new JButton("Copy Text");
        copyTextButton.setEnabled(false);
        pageLabel = new JLabel("Page: 1 / 0");
        toolBar.add(prevButton);
        toolBar.add(nextButton);
        toolBar.addSeparator();
        toolBar.add(zoomInButton);
        toolBar.add(zoomOutButton);
        toolBar.addSeparator();
        toolBar.add(copyTextButton);
        toolBar.addSeparator();
        toolBar.add(pageLabel);
        add(toolBar, BorderLayout.NORTH);

        // Create custom image label
        imageLabel = new CustomImageLabel();
        scrollPane = new JScrollPane(imageLabel);
        add(scrollPane, BorderLayout.CENTER);

        // Track mouse for selection and zooming
        mousePosition = new Point(0, 0);
        selectionStart = null;
        selectionEnd = null;
        isDragging = false;

        // Long press timer (500ms)
        longPressTimer = new Timer(500, e -> {
            if (selectionStart != null && !isDragging) {
                System.out.println("Long press detected at: " + selectionStart);
                copySelectedText();
                selectionStart = null;
                selectionEnd = null;
                copyTextButton.setEnabled(false);
                imageLabel.repaint();
            }
        });
        longPressTimer.setRepeats(false);

        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    selectionStart = e.getPoint();
                    selectionEnd = e.getPoint();
                    isDragging = false;
                    pressStartTime = System.currentTimeMillis();
                    copyTextButton.setEnabled(true);
                    longPressTimer.start();
                    System.out.println("Mouse pressed at: " + selectionStart + ", Button enabled: true");
                    imageLabel.repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                longPressTimer.stop();
                if (e.getButton() == MouseEvent.BUTTON1 && selectionStart != null) {
                    selectionEnd = e.getPoint();
                    System.out.println("Mouse released at: " + selectionEnd);
                    if (!isDragging && System.currentTimeMillis() - pressStartTime < 500) {
                        // Short click: zoom out
                        mousePosition = e.getPoint();
                        zoomOut();
                        selectionStart = null;
                        selectionEnd = null;
                        copyTextButton.setEnabled(false);
                        imageLabel.repaint();
                    } else if (isDragging) {
                        // Drag completed: keep selection for button copy
                        copyTextButton.setEnabled(true);
                        imageLabel.repaint();
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) { // Right-click
                    mousePosition = e.getPoint();
                    zoomIn();
                }
            }
        });

        imageLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mousePosition = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (selectionStart != null) {
                    longPressTimer.stop(); // Cancel long press during drag
                    isDragging = true;
                    selectionEnd = e.getPoint();
                    copyTextButton.setEnabled(true);
                    System.out.println("Dragging to: " + selectionEnd + ", Button enabled: true");
                    imageLabel.repaint();
                }
            }
        });

        // Action listeners
        openItem.addActionListener(e -> openPDF());
        prevButton.addActionListener(e -> previousPage());
        nextButton.addActionListener(e -> nextPage());
        zoomInButton.addActionListener(e -> zoomIn());
        zoomOutButton.addActionListener(e -> zoomOut());
        copyTextButton.addActionListener(e -> {
            if (selectionStart != null && selectionEnd != null) {
                copySelectedText();
                selectionStart = null;
                selectionEnd = null;
                copyTextButton.setEnabled(false);
                imageLabel.repaint();
            } else {
                copyPageText();
            }
        });
    }

    private void openPDF() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                if (document != null) {
                    document.close();
                }
                document = PDDocument.load(file);
                renderer = new PDFRenderer(document);
                currentPage = 0;
                zoom = 1.0f;
                selectionStart = null;
                selectionEnd = null;
                copyTextButton.setEnabled(false);
                System.out.println("New PDF loaded, Button enabled: false");
                if (!hasSelectableText()) {
                    JOptionPane.showMessageDialog(this,
                            "This PDF appears to be image-based. Text selection may not work. " +
                                    "Long-press or use the 'Copy Text' button to attempt copying, or convert " +
                                    "the PDF to a text-based format using an OCR tool (e.g., Adobe Acrobat, Tesseract).",
                            "Image-Based PDF Detected", JOptionPane.WARNING_MESSAGE);
                }
                updatePage();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error loading PDF: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean hasSelectableText() {
        if (document == null) return false;
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(currentPage + 1);
            stripper.setEndPage(currentPage + 1);
            String text = stripper.getText(document);
            return !text.trim().isEmpty();
        } catch (IOException ex) {
            System.out.println("Error checking text layer: " + ex.getMessage());
            return false;
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            selectionStart = null;
            selectionEnd = null;
            copyTextButton.setEnabled(false);
            System.out.println("Previous page, Button enabled: false");
            updatePage();
        }
    }

    private void nextPage() {
        if (document != null && currentPage < document.getNumberOfPages() - 1) {
            currentPage++;
            selectionStart = null;
            selectionEnd = null;
            copyTextButton.setEnabled(false);
            System.out.println("Next page, Button enabled: false");
            updatePage();
        }
    }

    private void zoomIn() {
        float oldZoom = zoom;
        zoom *= 1.2f;
        adjustViewport(oldZoom);
        updatePage();
    }

    private void zoomOut() {
        float oldZoom = zoom;
        zoom /= 1.2f;
        if (zoom < 0.1f) zoom = 0.1f;
        adjustViewport(oldZoom);
        updatePage();
    }

    private void adjustViewport(float oldZoom) {
        if (mousePosition == null) return;

        JViewport viewport = scrollPane.getViewport();
        Point viewPos = viewport.getViewPosition();
        Dimension viewSize = viewport.getExtentSize();

        double mouseX = mousePosition.getX();
        double mouseY = mousePosition.getY();

        double newMouseX = mouseX * (zoom / oldZoom);
        double newMouseY = mouseY * (zoom / oldZoom);

        int newX = (int) (newMouseX - viewSize.width / 2);
        int newY = (int) (newMouseY - viewSize.height / 2);

        newX = Math.max(0, Math.min(newX, imageLabel.getWidth() - viewSize.width));
        newY = Math.max(0, Math.min(newY, imageLabel.getHeight() - viewSize.height));

        viewport.setViewPosition(new Point(newX, newY));
    }

    private void copySelectedText() {
        if (document == null || selectionStart == null || selectionEnd == null) {
            System.out.println("Cannot copy: document or selection is null");
            copyPageText();
            return;
        }

        try {
            // Get page dimensions
            org.apache.pdfbox.pdmodel.common.PDRectangle cropBox = document.getPage(currentPage).getCropBox();
            float pageWidth = cropBox.getWidth();
            float pageHeight = cropBox.getHeight();

            // Get selection rectangle in image coordinates
            int x = Math.min(selectionStart.x, selectionEnd.x);
            int y = Math.min(selectionStart.y, selectionEnd.y);
            int width = Math.abs(selectionEnd.x - selectionStart.x);
            int height = Math.abs(selectionEnd.y - selectionStart.y);

            // Get image dimensions
            BufferedImage image = renderer.renderImageWithDPI(currentPage, 72 * zoom);
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            // Convert to PDF coordinates
            float scaleX = pageWidth / imageWidth;
            float scaleY = pageHeight / imageHeight;
            Rectangle pdfRect = new Rectangle(
                    (int) (x * scaleX),
                    (int) ((imageHeight - (y + height)) * scaleY), // Flip Y-axis
                    (int) (width * scaleX),
                    (int) (height * scaleY)
            );

            System.out.println("PDF selection rectangle: " + pdfRect);

            // Extract text
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.addRegion("selection", pdfRect);
            stripper.extractRegions(document.getPage(currentPage));
            String selectedText = stripper.getTextForRegion("selection").trim();

            if (!selectedText.isEmpty()) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection stringSelection = new StringSelection(selectedText);
                clipboard.setContents(stringSelection, null);
                JOptionPane.showMessageDialog(this, "Text copied to clipboard: " + selectedText, "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "No text found in selection. This may be an image-based PDF. " +
                                "Try copying the entire page's text using the 'Copy Text' button, " +
                                "or convert the PDF to a text-based format using an OCR tool (e.g., Adobe Acrobat, Tesseract).",
                        "No Text Found", JOptionPane.INFORMATION_MESSAGE);
                copyPageText();
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error copying text: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyPageText() {
        if (document == null) {
            JOptionPane.showMessageDialog(this, "No PDF loaded.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(currentPage + 1);
            stripper.setEndPage(currentPage + 1);
            String pageText = stripper.getText(document).trim();
            if (!pageText.isEmpty()) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection stringSelection = new StringSelection(pageText);
                clipboard.setContents(stringSelection, null);
                JOptionPane.showMessageDialog(this, "Entire page text copied to clipboard: " + pageText, "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "No selectable text found on this page. This is likely an image-based PDF. " +
                                "Please convert it to a text-based PDF using an OCR tool (e.g., Adobe Acrobat, Tesseract).",
                        "No Text Found", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error copying page text: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updatePage() {
        if (document == null) return;
        try {
            BufferedImage image = renderer.renderImageWithDPI(currentPage, 72 * zoom);
            imageLabel.setIcon(new ImageIcon(image));
            pageLabel.setText(String.format("Page: %d / %d", currentPage + 1, document.getNumberOfPages()));
            scrollPane.revalidate();
            scrollPane.repaint();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error rendering page: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PDFReader viewer = new PDFReader();
            viewer.setVisible(true);
        });
    }
}