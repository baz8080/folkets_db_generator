import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

import static java.lang.System.exit;

/**
 * Converts the folkets XML into a denormalised sqlite db. It is not beautiful.
 *
 * Created by barry on 15/08/2016.
 */
public class Main {

    private static final String COMMA = ",";
    private static final String VALUE_ATTRIBUTE = "value";
    private static final String TRANSLATION_NODE = "translation";

    @SuppressWarnings("UnusedAssignment")
    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            System.err.println("Expected 2 arguments");
            exit(1);
        }

        convertToDatabase(args[0]);
        convertToDatabase(args[1]);
    }

    private static void convertToDatabase(String xmlFilename) throws Exception {

        System.out.println("Converting: " + xmlFilename);

        Connection connection = DriverManager.getConnection("jdbc:sqlite:build/folkets.sqlite");
        connection.setAutoCommit(false);
        String tableName = xmlFilename.substring(0, xmlFilename.indexOf("_public"));

        Statement statement = connection.createStatement();
        statement.executeUpdate(String.format(Locale.US, "drop table if exists %s", tableName));
        statement.executeUpdate(String.format(Locale.US, "drop index if exists %s_idx_word", tableName));
        statement.executeUpdate(String.format(Locale.US, "drop index if exists %s_idx_word2", tableName));
        statement.executeUpdate(
                String.format(Locale.US, "create table %s(", tableName) +
                        "id INTEGER PRIMARY KEY ASC," +
                        "word TEXT, " +
                        "comment TEXT," +
                        "translations TEXT," +
                        "type TEXT," +
                        "inflections TEXT," +
                        "examples TEXT," +
                        "definition TEXT," +
                        "explanation TEXT," +
                        "phonetic TEXT," +
                        "synonyms TEXT," +
                        "saldos TEXT," +
                        "comparisons TEXT," +
                        "antonyms TEXT," +
                        "use TEXT," +
                        "variant TEXT," +
                        "idioms TEXT," +
                        "derivations TEXT," +
                        "compounds TEXT" +
                        ")"
        );
        statement.executeUpdate(String.format(Locale.US, "CREATE INDEX %s_idx_word ON %s (word)", tableName, tableName));
        statement.executeUpdate(String.format(Locale.US, "CREATE INDEX %s_idx_word2 ON %s (word COLLATE NOCASE)", tableName, tableName));
        statement.close();
        connection.commit();

        PreparedStatement preparedStatement =
                connection.prepareStatement("INSERT INTO " +
                        String.format(Locale.US, "%s(", tableName) +
                        "word, comment, translations, type, inflections, " +
                        "examples, definition, explanation, phonetic, " +
                        "synonyms, saldos, comparisons, antonyms, use, " +
                        "variant, idioms, derivations, compounds) " +
                        "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                );

        InputStream svToEnStream = Main.class.getResourceAsStream(xmlFilename);

        if (svToEnStream == null) {
            System.err.println("Inputsteam for folkets xml is null.");
            return;
        }

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(svToEnStream);

        NodeList wordsList = document.getElementsByTagName("word");
        System.out.println("Number of words: " + wordsList.getLength());

        Set<String> unknownElements = new HashSet<>();

        for (int i = 0; i < wordsList.getLength(); i++) {

            String wordValue = "";
            String wordTranslations = "";
            String wordClass = "";
            String wordComment = "";
            String wordInflections = "";
            String wordExamples = "";
            String wordDefinition = "";
            String wordExplanation = "";
            String wordPhonetic = "";
            String wordSynonyms = "";
            String wordSaldos = "";
            String wordComparisons = "";
            String wordAntonyms = "";
            String wordUse = "";
            String wordVariant = "";
            String wordIdioms = "";
            String wordDerivations = "";
            String wordCompounds = "";

            Node wordNode = wordsList.item(i);

            wordValue = getAttributeValue(wordNode, VALUE_ATTRIBUTE).replaceAll("\\|", "");
            wordClass = getAttributeValue(wordNode, "class");
            wordComment = getAttributeValue(wordNode, "comment");

            NodeList wordChildrenNodeList = wordNode.getChildNodes();

            List<String> examplesList = new ArrayList<>();
            List<String> translationsList = new ArrayList<>();
            List<String> synonymsList = new ArrayList<>();
            List<String> saldosList = new ArrayList<>();
            List<String> comparisonsList = new ArrayList<>();
            List<String> antonymsList = new ArrayList<>();
            List<String> idiomsList = new ArrayList<>();
            List<String> derivationsList = new ArrayList<>();
            List<String> compoundList = new ArrayList<>();

            for (int childIndex = 0; childIndex < wordChildrenNodeList.getLength(); childIndex++) {

                Node childNode = wordChildrenNodeList.item(childIndex);
                String childNodeName = childNode.getNodeName();

                if (TRANSLATION_NODE.equals(childNodeName)) {

                    translationsList.add(pipeDelimit(getAttributeValue(childNode, VALUE_ATTRIBUTE), getAttributeValue(childNode, "comment")));

                } else if ("paradigm".equals(childNodeName)) {

                    NodeList inflectionNodeList = childNode.getChildNodes();
                    List<String> inflections = new ArrayList<>();

                    for (int inflectionIndex = 0; inflectionIndex < inflectionNodeList.getLength(); inflectionIndex++) {

                        Node inflectionNode = inflectionNodeList.item(inflectionIndex);

                        if ("inflection".equals(inflectionNode.getNodeName())) {
                            inflections.add(getAttributeValue(inflectionNode, VALUE_ATTRIBUTE));
                        }
                    }

                    wordInflections = StringUtils.join(inflections, COMMA);

                } else if ("example".equals(childNodeName)) {

                    String example = getAttributeValue(childNode, VALUE_ATTRIBUTE);
                    String translation = getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE);
                    examplesList.add(pipeDelimit(example, translation));

                } else if ("definition".equals(childNodeName)) {

                    wordDefinition = pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE));


                } else if ("explanation".equals(childNodeName)) {

                    wordExplanation = pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE));

                } else if ("phonetic".equals(childNodeName)) {

                    // https://en.wikipedia.org/wiki/Help:IPA_for_Swedish
                    wordPhonetic = getPhonetic(childNode);

                } else if ("synonym".equals(childNodeName)) {

                    String attributeValue = getAttributeValue(childNode, VALUE_ATTRIBUTE);

                    if (attributeValue.contains(COMMA)) {
                        List<String> inlineSynonyms =
                                Arrays.asList(attributeValue.replaceAll(", ", COMMA).split(COMMA));
                        synonymsList.addAll(inlineSynonyms);
                    } else {
                        synonymsList.add(attributeValue);
                    }
                } else if ("see".equals(childNodeName)) {

                    String seeType = getAttributeValue(childNode, "type");
                    String seeValue = getAttributeValue(childNode, VALUE_ATTRIBUTE);

                    if ("saldo".equals(seeType)) {
                        saldosList.add(seeValue);
                    } else if ("compare".equals(seeType)) {
                        comparisonsList.add(seeValue);
                    }
                } else if ("related".equals(childNodeName)) {

                    antonymsList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE)));

                } else if ("use".equals(childNodeName)) {

                    wordUse = getAttributeValue(childNode, VALUE_ATTRIBUTE);

                } else if ("variant".equals(childNodeName)) {

                    wordVariant = getAttributeValue(childNode, VALUE_ATTRIBUTE);

                } else if ("idiom".equalsIgnoreCase(childNodeName)) {

                    idiomsList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE))
                    );

                } else if ("derivation".equalsIgnoreCase(childNodeName)) {

                    derivationsList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE))
                    );

                } else if ("compound".equalsIgnoreCase(childNodeName)) {

                    compoundList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE))
                    );

                } else {
                    if (childNodeName != null && !"#text".equals(childNodeName)) {
                        unknownElements.add(childNodeName);
                    }
                }
            }

            wordExamples = StringUtils.join(examplesList, COMMA);
            wordTranslations = StringUtils.join(translationsList, COMMA);
            wordSynonyms = StringUtils.join(synonymsList, COMMA);
            wordSaldos = StringUtils.join(saldosList, COMMA);
            wordComparisons = StringUtils.join(comparisonsList, COMMA);
            wordAntonyms = StringUtils.join(antonymsList, COMMA);
            wordIdioms = StringUtils.join(idiomsList, COMMA);
            wordDerivations = StringUtils.join(derivationsList, COMMA);
            wordCompounds = StringUtils.join(compoundList, COMMA);

            int columnIndex = 1;
            preparedStatement.setString(columnIndex++, wordValue);
            preparedStatement.setString(columnIndex++, wordComment);
            preparedStatement.setString(columnIndex++, wordTranslations);
            preparedStatement.setString(columnIndex++, wordClass);
            preparedStatement.setString(columnIndex++, wordInflections);
            preparedStatement.setString(columnIndex++, wordExamples);
            preparedStatement.setString(columnIndex++, wordDefinition);
            preparedStatement.setString(columnIndex++, wordExplanation);
            preparedStatement.setString(columnIndex++, wordPhonetic);
            preparedStatement.setString(columnIndex++, wordSynonyms);
            preparedStatement.setString(columnIndex++, wordSaldos);
            preparedStatement.setString(columnIndex++, wordComparisons);
            preparedStatement.setString(columnIndex++, wordAntonyms);
            preparedStatement.setString(columnIndex++, wordUse);
            preparedStatement.setString(columnIndex++, wordVariant);
            preparedStatement.setString(columnIndex++, wordIdioms);
            preparedStatement.setString(columnIndex++, wordDerivations);
            preparedStatement.setString(columnIndex++, wordCompounds);

            preparedStatement.addBatch();
        }

        preparedStatement.executeBatch();
        preparedStatement.close();
        connection.commit();

        connection.close();

        System.out.println("Converting: " + xmlFilename + " completed");
        System.out.println("Unprocessed elements: " + unknownElements);
    }

    private static String getPhonetic(Node childNode) {
        return getAttributeValue(childNode, VALUE_ATTRIBUTE)
                .replaceAll("@", "\u014B")      // ŋ
                .replaceAll(":", "\u02D0")      // ː
                .replaceAll("r\\+d", "\u0256")  // ɖ
                .replaceAll("r\\+s", "\u0282")  // ʂ
                .replaceAll("r\\+t", "\u0288")  // ʈ
                .replaceAll("r\\+l", "\u026D")  // ɭ
                .replaceAll("r\\+n", "\u0273")  // ɳ
                .replaceAll("\\$", "\u0267")    // ɧ
                .replaceAll("\\+o", "\u0289");  // ʉ
    }

    private static String pipeDelimit(String string1, String string2) {
        return string1 + "||" + string2;
    }

    private static String getAttributeValueInChild(Node parent, String childNodeName, String attributeName) {

        String attributeText = "";

        if (parent != null) {
            NodeList nodeList = parent.getChildNodes();

            if (nodeList.getLength() > 0) {

                Node targetNode = nodeList.item(0);

                if (childNodeName.equals(targetNode.getNodeName())) {
                    attributeText = getAttributeValue(targetNode, attributeName);
                }
            }
        }

        return attributeText;
    }

    private static String getAttributeValue(Node node, String attributeName) {

        String attributeText = "";

        if (node != null
                && node.hasAttributes()
                && node.getAttributes().getNamedItem(attributeName) != null) {
            attributeText = node.getAttributes().getNamedItem(attributeName).getTextContent()
                    .replaceAll("\\|", "")
                    .trim();
        }

        return StringEscapeUtils.unescapeHtml4(attributeText);
    }

}