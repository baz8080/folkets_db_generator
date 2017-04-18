import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

/**
 * Converts the folkets XML into a denormalised sqlite db. It is not beautiful.
 *
 * Created by barry on 15/08/2016.
 */
public class Main {

    private static final String COMMA = ",";
    private static final String SEPARATOR = "**";

    private static final String VALUE_ATTRIBUTE = "value";

    private static final String TRANSLATION_NODE = "translation";
    private static final String PARADIGM_NODE = "paradigm";
    private static final String EXAMPLE_NODE = "example";
    private static final String SYNONYM_NODE = "synonym";
    private static final String DEFINITION_NODE = "definition";
    private static final String EXPLANATION_NODE = "explanation";
    private static final String PHONETIC_NODE = "phonetic";
    private static final String SEE_NODE = "see";
    private static final String RELATED_NODE = "related";
    private static final String USE_NODE = "use";
    private static final String VARIANT_NODE = "variant";
    private static final String IDIOM_NODE = "idiom";
    private static final String DERIVATION_NODE = "derivation";
    private static final String COMPOUND_NODE = "compound";

    @SuppressWarnings("UnusedAssignment")
    public static void main(String[] args) throws Exception {

        convertToDatabase("en","folkets_en_sv_public.xml");
        convertToDatabase("sv", "folkets_sv_en_public.xml");
    }

    @SuppressWarnings("UnusedAssignment")
    private static void convertToDatabase(String baseLanguage, String xmlFilename) throws Exception {

        Connection connection = DriverManager.getConnection("jdbc:sqlite:build/folkets.sqlite");
        connection.setAutoCommit(false);

        String tableName = xmlFilename.substring(0, xmlFilename.indexOf("_public"));
        createTableAndIndices(tableName, connection);

        PreparedStatement preparedStatement = getPreparedStatement(connection, tableName);

        InputStream dbInputStream = Main.class.getResourceAsStream(xmlFilename);

        if (dbInputStream == null) {
            System.err.println("Inputsteam for folkets xml is null.");
            return;
        }

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(dbInputStream);

        NodeList wordsList = document.getElementsByTagName("word");

        System.out.println("Number of words: " + wordsList.getLength());

        BufferedWriter allWordsWriter = new BufferedWriter(new FileWriter("build/incomplete_words.txt"));

        Set<String> unknownElements = new HashSet<>();
        Set<String> wordClassTypes = new TreeSet<>();

        for (int i = 0; i < wordsList.getLength(); i++) {

            String wordValue = "", wordTypes = "", wordComment = "", wordInflections = "", wordDefinition = "",
                    wordExplanation = "", wordPhonetic = "", wordUse = "", wordVariant = "";

            List<String> examplesList = new ArrayList<>(), translationsList = new ArrayList<>(),
                    synonymsList = new ArrayList<>(), comparisonsList = new ArrayList<>(),
                    antonymsList = new ArrayList<>(), idiomsList = new ArrayList<>(),
                    derivationsList = new ArrayList<>(), compoundList = new ArrayList<>();

            Set<String> lemgramsSet = new TreeSet<>();

            Node wordNode = wordsList.item(i);

            wordValue = getWordValue(wordNode);
            wordTypes = getWordTypes(wordNode);
            wordComment = getAttributeValue(wordNode, "comment");

            NodeList wordChildrenNodeList = wordNode.getChildNodes();

            for (int childIndex = 0; childIndex < wordChildrenNodeList.getLength(); childIndex++) {

                Node childNode = wordChildrenNodeList.item(childIndex);
                String childNodeName = childNode.getNodeName();

                if (TRANSLATION_NODE.equals(childNodeName)) {

                    String comment = pipeDelimit(getAttributeValue(childNode, VALUE_ATTRIBUTE), getAttributeValue(childNode, "comment"));
                    translationsList.add(comment);

                } else if (PARADIGM_NODE.equals(childNodeName)) {

                    wordInflections = extractParadigmValues(childNode);

                } else if (EXAMPLE_NODE.equals(childNodeName)) {

                    extractExampleValues(examplesList, childNode);

                } else if (DEFINITION_NODE.equals(childNodeName)) {

                    wordDefinition = pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE));


                } else if (EXPLANATION_NODE.equals(childNodeName)) {

                    wordExplanation = pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE));

                } else if (PHONETIC_NODE.equals(childNodeName)) {

                    // https://en.wikipedia.org/wiki/Help:IPA_for_Swedish
                    wordPhonetic = getPhonetic(childNode);

                } else if (SYNONYM_NODE.equals(childNodeName)) {

                    extractSynonymValues(synonymsList, childNode);

                } else if (SEE_NODE.equals(childNodeName)) {

                    extractSeeValues(lemgramsSet, comparisonsList, childNode);

                } else if (RELATED_NODE.equals(childNodeName)) {

                    antonymsList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE)));

                } else if (USE_NODE.equals(childNodeName)) {

                    wordUse = getAttributeValue(childNode, VALUE_ATTRIBUTE);

                } else if (VARIANT_NODE.equals(childNodeName)) {

                    wordVariant = getAttributeValue(childNode, VALUE_ATTRIBUTE);

                } else if (IDIOM_NODE.equalsIgnoreCase(childNodeName)) {

                    idiomsList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE))
                    );

                } else if (DERIVATION_NODE.equalsIgnoreCase(childNodeName)) {

                    derivationsList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE))
                    );

                } else if (COMPOUND_NODE.equalsIgnoreCase(childNodeName)) {

                    compoundList.add(pipeDelimit(
                            getAttributeValue(childNode, VALUE_ATTRIBUTE),
                            getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE))
                    );

                } else {
                    if (childNodeName != null && !"#text".equals(childNodeName)) {
                        unknownElements.add(childNodeName);
                    }
                }

                String[] wordClassesArray = wordTypes.split(",");

                for (String wordClassValue : wordClassesArray) {
                    wordClassTypes.add(wordClassValue.trim());
                }
            }

            populateStatement(preparedStatement, baseLanguage, wordValue, wordTypes, wordComment, wordInflections,
                    wordDefinition, wordExplanation, wordPhonetic, wordUse, wordVariant, examplesList,
                    translationsList, synonymsList, lemgramsSet, comparisonsList, antonymsList, idiomsList,
                    derivationsList, compoundList);
        }

        preparedStatement.executeBatch();
        preparedStatement.close();
        connection.commit();

        connection.setAutoCommit(true);
        Statement vacuumStatement = connection.createStatement();
        vacuumStatement.execute("VACUUM;");

        connection.close();

        allWordsWriter.flush();
        allWordsWriter.close();

        System.out.println("Converting: " + xmlFilename + " completed");
        System.out.println("Unprocessed elements: " + unknownElements);
        System.out.println("Word class types: " + wordClassTypes);
    }

    private static void extractSynonymValues(List<String> synonymsList, Node childNode) {
        String attributeValue = getAttributeValue(childNode, VALUE_ATTRIBUTE);

        if (attributeValue.contains(COMMA)) {
            List<String> inlineSynonyms =
                    Arrays.asList(attributeValue.replaceAll(", ", COMMA).split(COMMA));
            synonymsList.addAll(inlineSynonyms);
        } else {
            synonymsList.add(attributeValue);
        }
    }

    private static void extractExampleValues(List<String> examplesList, Node childNode) {
        String example = getAttributeValue(childNode, VALUE_ATTRIBUTE);
        String translation = getAttributeValueInChild(childNode, TRANSLATION_NODE, VALUE_ATTRIBUTE);
        examplesList.add(pipeDelimit(example, translation));
    }

    private static String extractParadigmValues(Node childNode) {
        String wordInflections;
        NodeList inflectionNodeList = childNode.getChildNodes();
        List<String> inflections = new ArrayList<>();

        for (int inflectionIndex = 0; inflectionIndex < inflectionNodeList.getLength(); inflectionIndex++) {

            Node inflectionNode = inflectionNodeList.item(inflectionIndex);

            if ("inflection".equals(inflectionNode.getNodeName())) {
                inflections.add(getAttributeValue(inflectionNode, VALUE_ATTRIBUTE));
            }
        }

        wordInflections = StringUtils.join(inflections, SEPARATOR);
        return wordInflections;
    }

    private static String getWordTypes(Node wordNode) {
        return getAttributeValue(wordNode, "class")
                .replaceAll("jj", "av");
    }

    private static String getWordValue(Node wordNode) {
        return getAttributeValue(wordNode, VALUE_ATTRIBUTE).replaceAll("\\|", "");
    }

    private static void extractSeeValues(Set<String> lemgramsSet, List<String> comparisonsList, Node childNode) {
        String seeType = getAttributeValue(childNode, "type");

        if ("saldo".equals(seeType)) {
            String saldoValue = getSaldoValue(childNode);
            lemgramsSet.add(saldoValue);
        } else if ("compare".equals(seeType)) {
            comparisonsList.add(getAttributeValue(childNode, VALUE_ATTRIBUTE));
        }
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

    private static String getSaldoValue(Node node) {

        String lemgram = "";

        if (node != null
                && node.hasAttributes()
                && node.getAttributes().getNamedItem(VALUE_ATTRIBUTE) != null) {
            String attributeText = node.getAttributes().getNamedItem(VALUE_ATTRIBUTE).getTextContent()
                    .trim();

            // Saldo value should be in three || delimited parts, e.g.:
            // abc-bok||abc-bok..1||abc-bok..nn.1
            // [0] word form, [1] associations, [2] inflections
            String[] saldoAttributes = attributeText.split("\\|\\|");

            if (saldoAttributes.length == 3) {
                lemgram = saldoAttributes[2];
            }
        }

        return StringEscapeUtils.unescapeHtml4(lemgram);
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

    private static PreparedStatement getPreparedStatement(Connection connection, String tableName) throws SQLException {
        return connection.prepareStatement("INSERT INTO " +
                String.format(Locale.US, "%s(", tableName) +
                "language, word, comment, translations, types, inflections, " +
                "examples, definition, explanation, phonetic, " +
                "synonyms, lemgrams, comparisons, antonyms, use, " +
                "variant, idioms, derivations, compounds) " +
                "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );
    }

    private static String createTableAndIndices(String tableName, Connection connection) throws SQLException {

        Statement statement = connection.createStatement();

        statement.executeUpdate("drop table if exists android_metadata");
        statement.executeUpdate(String.format(Locale.US, "drop table if exists %s", tableName));
        statement.executeUpdate(String.format(Locale.US, "drop index if exists %s_idx_word", tableName));
        statement.executeUpdate(String.format(Locale.US, "drop index if exists %s_idx_word2", tableName));

        statement.executeUpdate("create table android_metadata (locale text)");
        statement.executeUpdate("insert into android_metadata values('en')");
        statement.executeUpdate("insert into android_metadata values('sv')");

        statement.executeUpdate(
                String.format(Locale.US, "create table %s(", tableName) +
                        "id INTEGER PRIMARY KEY ASC," +
                        "language TEXT, " +
                        "word TEXT, " +
                        "comment TEXT," +
                        "translations TEXT," +
                        "types TEXT," +
                        "inflections TEXT," +
                        "examples TEXT," +
                        "definition TEXT," +
                        "explanation TEXT," +
                        "phonetic TEXT," +
                        "synonyms TEXT," +
                        "lemgrams TEXT," +
                        "comparisons TEXT," +
                        "antonyms TEXT," +
                        "use TEXT," +
                        "variant TEXT," +
                        "idioms TEXT," +
                        "derivations TEXT," +
                        "compounds TEXT," +
                        "baseform TEXT" +
                        ")"
        );
        statement.executeUpdate(String.format(Locale.US, "CREATE INDEX %s_idx_word ON %s (word)", tableName, tableName));
        statement.executeUpdate(String.format(Locale.US, "CREATE INDEX %s_idx_word2 ON %s (word COLLATE NOCASE)", tableName, tableName));
        statement.close();
        connection.commit();
        return tableName;
    }

    @SuppressWarnings("UnusedAssignment")
    private static void populateStatement(PreparedStatement preparedStatement, String baseLanguage,
                                          String wordValue, String wordTypes, String wordComment, String wordInflections,
                                          String wordDefinition, String wordExplanation, String wordPhonetic,
                                          String wordUse, String wordVariant, List<String> examplesList,
                                          List<String> translationsList, List<String> synonymsList,
                                          Set<String> lemgramsSet, List<String> comparisonsList,
                                          List<String> antonymsList, List<String> idiomsList, List<String> derivationsList,
                                          List<String> compoundList) throws SQLException {
        int columnIndex = 1;

        preparedStatement.setString(columnIndex++, baseLanguage);
        preparedStatement.setString(columnIndex++, wordValue);
        preparedStatement.setString(columnIndex++, wordComment);
        preparedStatement.setString(columnIndex++, StringUtils.join(translationsList, SEPARATOR));
        preparedStatement.setString(columnIndex++, wordTypes);
        preparedStatement.setString(columnIndex++, wordInflections);
        preparedStatement.setString(columnIndex++, StringUtils.join(examplesList, SEPARATOR));
        preparedStatement.setString(columnIndex++, wordDefinition);
        preparedStatement.setString(columnIndex++, wordExplanation);
        preparedStatement.setString(columnIndex++, wordPhonetic);
        preparedStatement.setString(columnIndex++, StringUtils.join(synonymsList, SEPARATOR));
        preparedStatement.setString(columnIndex++, StringUtils.join(lemgramsSet, SEPARATOR));
        preparedStatement.setString(columnIndex++, StringUtils.join(comparisonsList, SEPARATOR));
        preparedStatement.setString(columnIndex++, StringUtils.join(antonymsList, SEPARATOR));
        preparedStatement.setString(columnIndex++, wordUse);
        preparedStatement.setString(columnIndex++, wordVariant);
        preparedStatement.setString(columnIndex++, StringUtils.join(idiomsList, SEPARATOR));
        preparedStatement.setString(columnIndex++, StringUtils.join(derivationsList, SEPARATOR));
        preparedStatement.setString(columnIndex++, StringUtils.join(compoundList, SEPARATOR));

        preparedStatement.addBatch();
    }
}