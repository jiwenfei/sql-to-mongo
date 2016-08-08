package com.fmaylinch.sqlmongo;

import com.codepoetics.protonpack.StreamUtils;
import com.fmaylinch.sqlmongo.parser.SqlParser;
import com.fmaylinch.sqlmongo.util.Fun;
import com.fmaylinch.sqlmongo.util.MongoUtil;
import com.mongodb.DB;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SqlMongo {

	private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static Pattern optionPattern = Pattern.compile("([a-zA-Z0-9]+)=(.+)");
	private static int padding;
	private static String nullValue;
	private static char csvSeparator;

	public static void main(String[] args) throws IOException {

		Properties config = setupConfig(args);

		String uri = getRequiredPropertyWithExample(config, "uri",
				"mongodb://localhost:27017/mydb");
		String querySql = getRequiredPropertyWithExample(config, "query",
				"select userEmail from coupons where couponState = 4");

		DB db = MongoUtil.connectToDb(uri);

		SqlParser.Result result = new SqlParser(querySql, db).parse();

		printOutput(result, config);
	}

	private static Properties setupConfig(String[] args)
	{
		// Configure defaults
		Properties config = new Properties();
		config.setProperty("dateFormat", "yyyy-MM-dd HH:mm:ss");
		config.setProperty("nullValue", "");
		config.setProperty("output", "horizontal"); // horizontal, vertical or directly a csv file name
		config.setProperty("padding", "40"); // only used for horizontal and vertical output
		config.setProperty("csvSeparator", ","); // only used for csv output

		try {
			config.load(new FileReader("config.properties"));
		} catch (IOException e) {
			// Ignore
		}

		overrideConfigFromArgs(args, config);

		dateFormat = new SimpleDateFormat(config.getProperty("dateFormat"));
		nullValue = config.getProperty("nullValue");
		padding = Integer.parseInt(config.getProperty("padding"));
		csvSeparator = config.getProperty("csvSeparator").charAt(0);

		return config;
	}

	private static void printOutput(SqlParser.Result result, Properties config) throws IOException
	{
		String output = config.getProperty("output");

		if (result.fields.isEmpty() && !output.equals("vertical")) {
			System.err.println("If you retrieve all fields you must use vertical output. Forcing vertical output.");
			output = "vertical";
		}

		switch (output) {
			case "horizontal":
				printResultHorizontal(result);
				break;
			case "vertical":
				printResultVertical(result);
				break;
			default:
				printResultToCsv(result, output);
				break;
		}
	}

	private static void printResultHorizontal(SqlParser.Result result) {

		System.out.println(StringUtils.join(Fun.map(result.fields.keySet(), f -> StringUtils.rightPad(f, padding)), ""));

		for (SqlParser.Result.Doc doc : result) {
			List<String> values = extractValues(doc, result.fields.values());
			System.out.println(StringUtils.join(Fun.map(values, f -> StringUtils.rightPad(f, padding)), ""));
		}
	}

	private static void printResultVertical(SqlParser.Result result) {

		for (SqlParser.Result.Doc doc : result) {

			Collection<String> aliases = !result.fields.isEmpty() ? result.fields.keySet() : doc.getFieldNames();
			Collection<String> fields = !result.fields.isEmpty() ? result.fields.values() : doc.getFieldNames();

			List<String> values = extractValues(doc, fields);

			List<String> fieldsAndValues = StreamUtils
					.zip(aliases.stream(), values.stream(), (f, v) -> StringUtils.rightPad(f + ":", padding) + v)
					.collect(Collectors.toList());

			System.out.println(StringUtils.join(fieldsAndValues, "\n"));
			System.out.println();
		}
	}

	private static void printResultToCsv(SqlParser.Result result, String csvFile) throws IOException
	{
		System.out.println("Writing output to CSV file: " + csvFile + " ...");
		CSVWriter writer = new CSVWriter(new FileWriter(csvFile), csvSeparator);

		writer.writeNext(toStringArray(result.fields.keySet())); // header

		for (SqlParser.Result.Doc doc : result) {

			List<String> values = extractValues(doc, result.fields.values());
			writer.writeNext(toStringArray(values));
		}

		writer.close();
		System.out.println("Done");

	}

	private static List<String> extractValues(SqlParser.Result.Doc doc, Collection<String> fieldNames)
	{
		return Fun.map(fieldNames, f -> valueToString(doc.getValue(f)));
	}

	private static String valueToString(Object value) {
		if (value == null) return nullValue;
		if (value instanceof Date) return dateFormat.format(value);
		return value.toString();
	}

	private static String[] toStringArray(Collection<String> list) {
		return list.toArray(new String[list.size()]);
	}

	private static String getRequiredPropertyWithExample(Properties config, String property, String example)
	{
		String value = config.getProperty(property);
		if (StringUtils.isEmpty(value)) {
			System.err.println("Please provide " + property + " through `config.properties` file or command line option e.g. \"" + property + "=" + example + "\"");
			System.exit(0);
		}
		return value;
	}

	private static void overrideConfigFromArgs(String[] args, Properties config)
	{
		for (String arg : args)
		{
			Matcher matcher = optionPattern.matcher(arg);
			if (!matcher.matches())
				throw new IllegalArgumentException("Unexpected option: " + arg + " (format: key=value)");

			config.setProperty(matcher.group(1), matcher.group(2));
		}
	}
}
