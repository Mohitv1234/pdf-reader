import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.logging.Level;

public class PDFReader {
    private static final Logger LOGGER = Logger.getLogger(PDFReader.class.getName());

    static {
        // Suppress PDFBox warnings
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.SEVERE);
    }

    private PDDocument document;
    private int currentPage;
    private int totalPages;

    public PDFReader(String filePath) throws IOException {
        File file = new File(filePath);
        document = PDDocument.load(file);
        currentPage = 1;
        totalPages = document.getNumberOfPages();
    }

    public void displayMetadata() {
        PDDocumentInformation info = document.getDocumentInformation();
        System.out.println("PDF Metadata:");
        System.out.println("Title: " + info.getTitle());
        System.out.println("Author: " + info.getAuthor());
        System.out.println("Total Pages: " + totalPages);
    }

    public void displayCurrentPage() throws IOException {
        if (currentPage < 1 || currentPage > totalPages) {
            System.out.println("Invalid page number!");
            return;
        }
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(currentPage);
        stripper.setEndPage(currentPage);
        String text = stripper.getText(document);
        System.out.println("\n--- Page " + currentPage + " ---");
        System.out.println(text);
    }

    public void nextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            System.out.println("Moved to page " + currentPage);
        } else {
            System.out.println("Already on the last page!");
        }
    }

    public void previousPage() {
        if (currentPage > 1) {
            currentPage--;
            System.out.println("Moved to page " + currentPage);
        } else {
            System.out.println("Already on the first page!");
        }
    }

    public void close() throws IOException {
        if (document != null) {
            document.close();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the path to the PDF file:");
        String filePath = scanner.nextLine();

        try {
            PDFReader reader = new PDFReader(filePath);
            reader.displayMetadata();

            while (true) {
                System.out.println("\nPDF Reader Menu:");
                System.out.println("1. Display current page");
                System.out.println("2. Next page");
                System.out.println("3. Previous page");
                System.out.println("4. Exit");
                System.out.print("Choose an option: ");
                int choice = scanner.nextInt();

                switch (choice) {
                    case 1:
                        reader.displayCurrentPage();
                        break;
                    case 2:
                        reader.nextPage();
                        break;
                    case 3:
                        reader.previousPage();
                        break;
                    case 4:
                        reader.close();
                        System.out.println("Exiting PDF Reader.");
                        scanner.close();
                        return;
                    default:
                        System.out.println("Invalid option! Please try again.");
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing PDF: " + e.getMessage(), e);
        }
    }
}