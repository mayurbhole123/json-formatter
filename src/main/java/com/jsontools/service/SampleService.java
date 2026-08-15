package com.jsontools.service;

import com.jsontools.model.Tool;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Supplies the "Load Sample" content for each tool. */
@Service
public class SampleService {

    private static final String JSON_SAMPLE = """
            {
              "store": {
                "name": "Corner Books",
                "open": true,
                "established": 1994,
                "book": [
                  {
                    "category": "fiction",
                    "author": "Ursula K. Le Guin",
                    "title": "The Dispossessed",
                    "price": 9.99,
                    "tags": ["sci-fi", "classic"]
                  },
                  {
                    "category": "reference",
                    "author": "Kernighan and Ritchie",
                    "title": "The C Programming Language",
                    "price": 24.5,
                    "isbn": "0-13-110362-8",
                    "tags": ["programming"]
                  }
                ],
                "address": {
                  "street": "12 Mill Lane",
                  "city": "Bristol",
                  "postcode": "BS1 4TR"
                }
              },
              "lastUpdated": null
            }""";

    private static final String JSON_SAMPLE_RIGHT = """
            {
              "store": {
                "name": "Corner Books & Coffee",
                "open": true,
                "established": 1994,
                "book": [
                  {
                    "category": "fiction",
                    "author": "Ursula K. Le Guin",
                    "title": "The Dispossessed",
                    "price": 11.99,
                    "tags": ["sci-fi", "classic"]
                  },
                  {
                    "category": "reference",
                    "author": "Kernighan and Ritchie",
                    "title": "The C Programming Language",
                    "price": 24.5,
                    "isbn": "0-13-110362-8",
                    "tags": ["programming"]
                  }
                ],
                "address": {
                  "street": "12 Mill Lane",
                  "city": "Bristol",
                  "postcode": "BS1 4TR",
                  "country": "UK"
                }
              }
            }""";

    private static final String SCHEMA_SAMPLE = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["store"],
              "properties": {
                "store": {
                  "type": "object",
                  "required": ["name", "book"],
                  "properties": {
                    "name": { "type": "string", "minLength": 1 },
                    "open": { "type": "boolean" },
                    "established": { "type": "integer", "minimum": 1800 },
                    "book": {
                      "type": "array",
                      "minItems": 1,
                      "items": {
                        "type": "object",
                        "required": ["title", "price"],
                        "properties": {
                          "title": { "type": "string" },
                          "price": { "type": "number", "exclusiveMinimum": 0 }
                        }
                      }
                    }
                  }
                }
              }
            }""";

    private static final String TABULAR_JSON_SAMPLE = """
            [
              { "id": 1, "name": "Ada Lovelace",   "role": "Analyst",  "active": true,  "score": 98.5 },
              { "id": 2, "name": "Alan Turing",    "role": "Engineer", "active": true,  "score": 99.1 },
              { "id": 3, "name": "Grace Hopper",   "role": "Admiral",  "active": false, "score": 97.3 },
              { "id": 4, "name": "Katherine Johnson", "role": "Mathematician", "active": true, "score": 99.8 }
            ]""";

    private static final String XML_SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <store name="Corner Books" open="true">
              <address>
                <street>12 Mill Lane</street>
                <city>Bristol</city>
                <postcode>BS1 4TR</postcode>
              </address>
              <book category="fiction">
                <title>The Dispossessed</title>
                <author>Ursula K. Le Guin</author>
                <price>9.99</price>
              </book>
              <book category="reference">
                <title>The C Programming Language</title>
                <author>Kernighan and Ritchie</author>
                <price>24.50</price>
              </book>
            </store>""";

    private static final String YAML_SAMPLE = """
            store:
              name: Corner Books
              open: true
              established: 1994
              address:
                street: 12 Mill Lane
                city: Bristol
                postcode: BS1 4TR
              book:
                - category: fiction
                  title: The Dispossessed
                  author: Ursula K. Le Guin
                  price: 9.99
                  tags: [sci-fi, classic]
                - category: reference
                  title: The C Programming Language
                  author: Kernighan and Ritchie
                  price: 24.5
            lastUpdated: null""";

    private static final String CSV_SAMPLE = """
            id,name,role,active,score
            1,Ada Lovelace,Analyst,true,98.5
            2,Alan Turing,Engineer,true,99.1
            3,Grace Hopper,Admiral,false,97.3
            4,"Johnson, Katherine",Mathematician,true,99.8""";

    private static final String TSV_SAMPLE =
            "id\tname\trole\tactive\tscore\n"
            + "1\tAda Lovelace\tAnalyst\ttrue\t98.5\n"
            + "2\tAlan Turing\tEngineer\ttrue\t99.1\n"
            + "3\tGrace Hopper\tAdmiral\tfalse\t97.3\n"
            + "4\tKatherine Johnson\tMathematician\ttrue\t99.8";

    private static final String TEXT_SAMPLE = """
            The Dispossessed
            "An ambiguous utopia" - price 9.99
            Tab	separated	values, and a backslash \\ too.""";

    private static final String ESCAPED_SAMPLE =
            "\"{\\\"store\\\":{\\\"name\\\":\\\"Corner Books\\\",\\\"open\\\":true,\\\"established\\\":1994}}\"";

    private static final String BASE64_SAMPLE =
            "eyJzdG9yZSI6eyJuYW1lIjoiQ29ybmVyIEJvb2tzIiwib3BlbiI6dHJ1ZX19";

    private static final String URL_ENCODED_SAMPLE =
            "q=corner+books%26coffee&city=Bristol%2C+UK&path=%2Fbooks%2Fsci-fi";

    private static final String BROKEN_JSON_SAMPLE = """
            {
              // the trailing comma, comments and single quotes below are all invalid JSON
              name: 'Corner Books',
              'open': true,
              established: 1994,
              tags: ['books', 'coffee',],
            }""";

    /** Tools whose sample is not simply "the sample for my input syntax". */
    private static final Map<String, String> OVERRIDES = Map.ofEntries(
            Map.entry("json-fixer", BROKEN_JSON_SAMPLE),
            Map.entry("json-to-csv", TABULAR_JSON_SAMPLE),
            Map.entry("json-to-tsv", TABULAR_JSON_SAMPLE),
            Map.entry("json-to-excel", TABULAR_JSON_SAMPLE),
            Map.entry("json-to-sql", TABULAR_JSON_SAMPLE),
            Map.entry("json-to-html", TABULAR_JSON_SAMPLE),
            Map.entry("tsv-to-json", TSV_SAMPLE),
            Map.entry("string-to-json", ESCAPED_SAMPLE),
            Map.entry("json-unescape", ESCAPED_SAMPLE),
            Map.entry("base64-decode", BASE64_SAMPLE),
            Map.entry("url-decode", URL_ENCODED_SAMPLE));

    public String sampleFor(Tool tool) {
        String override = OVERRIDES.get(tool.getId());
        if (override != null) {
            return override;
        }
        return switch (tool.getInputEditor()) {
            case XML -> XML_SAMPLE;
            case YAML -> YAML_SAMPLE;
            case CSV -> CSV_SAMPLE;
            case TEXT -> TEXT_SAMPLE;
            default -> JSON_SAMPLE;
        };
    }

    /** Sample for the second pane of the dual-input tools. */
    public String secondSampleFor(Tool tool) {
        if (!tool.isDualInput()) {
            return "";
        }
        return "json-schema-validator".equals(tool.getId()) ? SCHEMA_SAMPLE : JSON_SAMPLE_RIGHT;
    }
}
