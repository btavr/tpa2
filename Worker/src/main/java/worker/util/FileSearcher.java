package worker.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitário para pesquisa de substrings em ficheiros .txt
 * Baseado no Anexo 2 do enunciado
 */
public class FileSearcher {

    /**
     * Pesquisa ficheiros que contêm todas as substrings especificadas
     * @param directoryPath Diretoria onde procurar
     * @param substringsList Lista de substrings a procurar
     * @return Map com nome do ficheiro -> conteúdo do email
     */
    public static Map<String, String> searchInsideEmails(String directoryPath, List<String> substringsList)
            throws IOException {
        Map<String, String> matchingEmails = new HashMap<>();

        Files.list(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".txt"))
                .forEach(path -> {
                    try {
                        String emailMessage = Files.readString(path);
                        if (containsAllSubstrings(emailMessage, substringsList)) {
                            System.out.println("caminho: "+ path.getFileName().toString());
                            matchingEmails.put(path.getFileName().toString(), emailMessage);
                        }
                    } catch (IOException e) {
                        System.err.println("Read error in file: " + path + " - " + e.getMessage());
                    }
                });

        return matchingEmails;
    }

    /**
     * Verifica se a mensagem contém todas as substrings (case-insensitive)
     */
    public static boolean containsAllSubstrings(String message, List<String> substringsList) {
        String lowerMessage = message.toLowerCase();
        for (String substr : substringsList) {
            if (!lowerMessage.contains(substr.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Obtém o conteúdo de um ficheiro específico
     */
    public static String getFileContent(String directoryPath, String filename) throws IOException {
        Path filePath = Paths.get(directoryPath, filename);
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            return Files.readString(filePath);
        }
        throw new IOException("File not found: " + filename);
    }

    /**
     * Obtém lista de nomes de ficheiros que contêm todas as substrings
     */
    public static List<String> getMatchingFilenames(String directoryPath, List<String> substringsList)
            throws IOException {
        Map<String, String> matchingEmails = searchInsideEmails(directoryPath, substringsList);
        return new ArrayList<>(matchingEmails.keySet());
    }
}

