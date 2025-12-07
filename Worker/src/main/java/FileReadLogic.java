import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileReadLogic {

    public static List<String> searchInsideEmails(String directoryPath, List<String> substringsList) throws IOException {
        final List<String> fileNameWithMatchingEmails = new ArrayList<>();

        System.out.println("DirectoryPath -> " + directoryPath);
        System.out.println("Substrings -> ");
        for (final String substring: substringsList) {
            System.out.print(substring + " / ");
        }
        System.out.println();

        Files.list(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .forEach(path -> {
                    try {
                        String emailMessage = Files.readString(path);
                        if (containsAllSubstrings(emailMessage, substringsList)) {
                            System.out.println("Adicionou o email com path: " + path.toString());
                            fileNameWithMatchingEmails.add(path.toString());
                        }
                    } catch (IOException e) {
                        System.err.println("Read error in file: " + path + " - " + e.getMessage());
                    }
                });
        return fileNameWithMatchingEmails;
    }

    public static String readEmail(String fileNamePath) throws IOException {
        // TODO: Validar com o professor se podemos receber outros tipos de ficheiros para ler ou podemos deixar hardcoded ".txt".

        return Files.readString(Paths.get(fileNamePath));
    }

    // Do professor
    public static Map<String, String> searchInsideEmailsProfessor(String directoryPath, List<String> substringsList)
            throws IOException {
        Map<String, String> matchingEmails = new HashMap<>();
        // Percorrer todos os ficheiros .txt na diretoria
        Files.list(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .forEach(path -> {
                    try {
                        String emailMessage = Files.readString(path);
                        if (containsAllSubstrings(emailMessage, substringsList)) {
                            matchingEmails.put(path.toString(), emailMessage);
                        }
                    } catch (IOException e) {
                        System.err.println("Read error in file: " + path + " - " + e.getMessage());
                    }
                });
        return matchingEmails;
    }

    public static boolean containsAllSubstrings(String message, List<String> substringsList) {
        String lowerMessage = message.toLowerCase();
        for (String substr : substringsList) {
            if (!lowerMessage.contains(substr.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

}
