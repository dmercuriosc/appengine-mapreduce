package com.google.appengine.tools.mapreduce;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.appengine.tools.mapreduce.impl.BigQueryMarshallerByType;
import com.google.appengine.tools.mapreduce.impl.BigqueryFieldMarshaller;
import com.google.appengine.tools.mapreduce.testmodels.Child;
import com.google.appengine.tools.mapreduce.testmodels.ClassExtendingAbstract;
import com.google.appengine.tools.mapreduce.testmodels.ClassWithArray;
import com.google.appengine.tools.mapreduce.testmodels.Father;
import com.google.appengine.tools.mapreduce.testmodels.Man;
import com.google.appengine.tools.mapreduce.testmodels.Person;
import com.google.appengine.tools.mapreduce.testmodels.PhoneNumber;
import com.google.appengine.tools.mapreduce.testmodels.SampleClassWithNestedCollection;
import com.google.appengine.tools.mapreduce.testmodels.SampleClassWithNonParametricList;
import com.google.appengine.tools.mapreduce.testmodels.SimplAnnotatedJson;
import com.google.appengine.tools.mapreduce.testmodels.SimpleJson;
import com.google.appengine.tools.mapreduce.testmodels.SimpleJsonWithWrapperTypes;
import com.google.common.collect.Lists;

import com.fasterxml.jackson.databind.ObjectMapper;

import junit.framework.TestCase;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BigQueryDataMarshallerTest extends TestCase {


  private class BigQueryDataMarshallerTester<T> {
    private static final String NEWLINE_CHARACTER = "\n";

    BigQueryMarshaller<T> marshaller;

    public BigQueryDataMarshallerTester(BigQueryMarshaller<T> marshaller) {
      this.marshaller = marshaller;
    }

    /**
     * @param expected json string generated by the marshaller
     */
    public void testGeneratedJson(String expected, T actual) {
      String actualJson = toJsonString(actual);
      assertTrue(actualJson.contains(NEWLINE_CHARACTER));
      String fromBuf = null;
      ByteBuffer backToBuf = null;
      try {
        // Testing the ByteBuffer can be coded/decoded into same string
        backToBuf = UTF_8.encode(actualJson);
        fromBuf = UTF_8.newDecoder().decode(backToBuf).toString();
        assertEquals(actualJson, fromBuf);
      } catch (CharacterCodingException e1) {
        throw new RuntimeException("Could not decode the string", e1);
      }
      ObjectMapper mapper = new ObjectMapper();
      try {
        assertTrue(mapper.readTree(expected).equals(mapper.readTree(actualJson)));
      } catch (IOException e) {
        fail("Exception while serializing. Expected " + expected + " got " + actualJson);
      }
    }

    private String toJsonString(T value) {
      ByteBuffer buf = marshaller.toBytes(value);
      String jsonString = null;
      try {
        jsonString = UTF_8.newDecoder().decode(buf).toString();
      } catch (CharacterCodingException e1) {
        throw new RuntimeException("Could not decode the string", e1);
      }
      return jsonString;
    }

    /**
     * asserts each field of expected schema and generated schema
     *
     * @param expected {@code TableSchema}
     */
    public void testSchema(TableSchema expected) {

      List<TableFieldSchema> nonRecordExpFields = getAllNonRecordFields(expected.getFields());
      List<TableFieldSchema> nonRecordActFields =
          getAllNonRecordFields(marshaller.getSchema().getFields());

      Comparator<TableFieldSchema> fieldSchemaComprator = new Comparator<TableFieldSchema>() {
        @Override
        public int compare(TableFieldSchema o1, TableFieldSchema o2) {
          return o1.toString().compareTo(o2.toString());
        }

      };
      Collections.sort(nonRecordActFields, fieldSchemaComprator);
      Collections.sort(nonRecordExpFields, fieldSchemaComprator);

      assertEquals(nonRecordExpFields.size(), nonRecordActFields.size());
      assertEquals(nonRecordExpFields, nonRecordActFields);
    }

    /**
     * Recursively retrieves all the simple type fields from the fields of type "record".
     */
    private List<TableFieldSchema> getAllNonRecordFields(List<TableFieldSchema> fields) {
      List<TableFieldSchema> toRet = Lists.newArrayList();
      for (TableFieldSchema tfs : fields) {
        if (tfs.getType().equals("record")) {
          toRet.addAll(getAllNonRecordFields(tfs.getFields()));
        } else {
          toRet.add(tfs);
        }
      }
      return toRet;
    }
  }

  public void testGeneratedJsonForSimpleFields() {
    BigQueryDataMarshallerTester<SimpleJson> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(SimpleJson.class));
    tester.testGeneratedJson("{\"name\":\"test\",\"id\":1}", new SimpleJson("test", 1));
  }

  public void testGeneratedJsonForAnnotatedName() {
    BigQueryDataMarshallerTester<SimplAnnotatedJson> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(SimplAnnotatedJson.class));

    tester.testGeneratedJson("{\"niceName\":\"someName\",\"id\":\"456\",\"intField\":55}",
        new SimplAnnotatedJson("someName", "456", 55));
  }

  public void testGeneratedJsonForArrayField() {
    BigQueryDataMarshallerTester<ClassWithArray> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(ClassWithArray.class));

    tester.testGeneratedJson("{\"id\":345,\"values\":[\"1\",\"2\",\"3\"],\"name\":\"arrayClass\"}",
        new ClassWithArray(345, "arrayClass", new String[] {"1", "2", "3"}));
  }

  public void testGeneratedJsonForNestedFields() {
    BigQueryDataMarshallerTester<Person> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(Person.class));

    tester.testGeneratedJson(
        "{\"fullName\":\"Joe\",\"age\":45,\"height\":5.8,\"weight\":100.0,\"gender\":\"male\""
        + ",\"phoneNumber\":{\"areaCode\":404,\"number\":5686}}",
        new Person(
            "Joe",
            45,
            5.8,
            100,
            "male",
            new PhoneNumber(404, 5686)));
  }

  public void testGeneratedJsonForBigIgnoreAnnotations() {
    BigQueryDataMarshallerTester<Man> tester =
        new BigQueryDataMarshallerTester<>(new BigQueryMarshallerByType<>(Man.class));
    tester.testGeneratedJson("{\"name\":\"Iniesta\",\"gender\":\"male\"}",
        new Man(454, "Iniesta", "male"));
  }

  public void testGeneratedJsonForRepeatedNestedRecord() {
    BigQueryDataMarshallerTester<Father> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(Father.class));

    tester.testGeneratedJson(
        "{\"married\":true,\"name\":\"Messi\",\"sons\":[{\"fullName\":\"Ronaldo\",\"age\":28},"
        + "{\"fullName\":\"Rooney\",\"age\":29}]}", new Father(true, "Messi",
        Lists.newArrayList(new Child("Ronaldo", 28), new Child("Rooney", 29))));
  }

  public void testGeneratedJsonForFieldWithNullValue() {
    BigQueryDataMarshallerTester<SimpleJson> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(SimpleJson.class));
    try {
      tester.testGeneratedJson("{\"id\":1}", new SimpleJson(null, 1));
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Non-nullable field name."
          + " This field is either annotated as REQUIRED or is a primitive type.",
          e.getMessage());
      return;
    }
    fail();
  }

  public void testGeneratedJsonForClassWithWrapperType() {
    BigQueryDataMarshallerTester<SimpleJsonWithWrapperTypes> tester =
        new BigQueryDataMarshallerTester<>(
            new BigQueryMarshallerByType<>(SimpleJsonWithWrapperTypes.class));
    tester.testGeneratedJson("{\"name\":\"test\",\"id\":1, \"value\":1.5}",
        new SimpleJsonWithWrapperTypes(Integer.valueOf(1), "test", Float.valueOf("1.5")));
  }

  public void testGeneratedJsonForTypesWithNonParameterizedCollection() {
    BigQueryDataMarshallerTester<SampleClassWithNonParametricList> tester =
        new BigQueryDataMarshallerTester<>(
            new BigQueryMarshallerByType<>(SampleClassWithNonParametricList.class));
    try {
      tester.testGeneratedJson("{\"name\":\"test\",\"id\":1, \"value\":1.5}",
          new SampleClassWithNonParametricList(Lists.newArrayList()));
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Cannot marshal a non-parameterized Collection field " + "l" + " into BigQuery data",
          e.getMessage());
      return;
    }
    fail();
  }

  public void testGeneratedJsonForTypesWithNestedCollection() {
    BigQueryDataMarshallerTester<SampleClassWithNestedCollection> tester =
        new BigQueryDataMarshallerTester<>(
            new BigQueryMarshallerByType<>(SampleClassWithNestedCollection.class));
    List<List<String>> toTest = new ArrayList<>();
    toTest.add(Lists.newArrayList("", ""));
    try {
      tester.testGeneratedJson("{\"name\":\"test\",\"id\":1, \"value\":1.5}",
          new SampleClassWithNestedCollection(toTest));
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Invalid field. Cannot marshal fields of type Collection<GenericType> or GenericType[].",
          e.getMessage());
      return;
    }
    fail();
  }

  private class ClassForInnerClassTest {
    @SuppressWarnings("unused")
    int id;
    @SuppressWarnings("unused")
    String name;

    public ClassForInnerClassTest(int id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  public void testGeneratedJsonForInnerClass() {
    BigQueryDataMarshallerTester<ClassForInnerClassTest> tester =
        new BigQueryDataMarshallerTester<>(
            new BigQueryMarshallerByType<>(ClassForInnerClassTest.class));
    tester.testGeneratedJson("{\"name\":\"test\",\"id\":1}",
        this.new ClassForInnerClassTest(1, "test"));
  }

  public void testGeneratedJsonForClassWithCurrencyType() {
    BigQueryDataMarshallerTester<ClassWithCurrency> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(ClassWithCurrency.class));
    tester.testGeneratedJson("{\"currency\":\"USD\",\"id\":1}",
        new ClassWithCurrency(Currency.getInstance("USD"), 1));
  }

  private static class ClassWithCurrency {
    @SuppressWarnings("unused")
    Currency currency;
    @SuppressWarnings("unused")
    int id;

    public ClassWithCurrency(Currency currency, int id) {
      this.currency = currency;
      this.id = id;
    }
  }

  @SuppressWarnings("unused")
  public void testGeneratedJsonForClassWithNumberType() {
    try {
      new BigQueryDataMarshallerTester<ClassWithNumber>(
          new BigQueryMarshallerByType<ClassWithNumber>(ClassWithNumber.class));
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Cannot marshal " + Number.class.getSimpleName()
              + ". Interfaces and abstract class cannot be cannot be marshalled into consistent"
              + " BigQuery data.",
          e.getMessage());
      return;
    }
    fail();
  }

  private static class ClassWithNumber {
    @SuppressWarnings("unused")
    Number number;
    @SuppressWarnings("unused")
    int id;

    @SuppressWarnings("unused")
    public ClassWithNumber(Number number, int id) {
      this.number = number;
      this.id = id;
    }
  }

  @SuppressWarnings("unused")
  public void testGeneratedJsonForClassWithUnparameterizedMap() {
    try {
      new BigQueryDataMarshallerTester<ClassWithMap>(
          new BigQueryMarshallerByType<ClassWithMap>(ClassWithMap.class));
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Cannot marshal " + Map.class.getSimpleName()
              + ". Interfaces and abstract class cannot be cannot be marshalled into consistent"
              + " BigQuery data.",
          e.getMessage());
      return;
    }
    fail();
  }

  private static class ClassWithMap {
    @SuppressWarnings({"rawtypes", "unused"})
    Map map;
    @SuppressWarnings("unused")
    int id;

    @SuppressWarnings({"unused", "rawtypes"})
    public ClassWithMap(Map map, int id) {
      this.map = map;
      this.id = id;
    }
  }

  @SuppressWarnings("unused")
  public void testGeneratedJsonForClassWithRecord() {
    BigQueryDataMarshallerTester<ClassWithRecord> tester =
      new BigQueryDataMarshallerTester<ClassWithRecord>(
        new BigQueryMarshallerByType<ClassWithRecord>(ClassWithRecord.class));

    tester.testGeneratedJson("{}",
     new ClassWithRecord(null,null));

    tester.testGeneratedJson("{\"record\":{\"name\":\"myname\",\"value\":\"myvalue\"}}",
     new ClassWithRecord(new ClassWithRecord.Record("myname","myvalue"), null));

    tester.testGeneratedJson(
     "{\"record\":{\"name\":\"name\",\"value\":\"value\"},\"records\":[{\"name\":\"name\",\"value\":\"value\"}]}",
     new ClassWithRecord(new ClassWithRecord.Record("name","value"),
      Lists.newArrayList(new ClassWithRecord.Record("name","value"))));


    tester.testSchema(new TableSchema().setFields(Lists.newArrayList(new TableFieldSchema()
        .setName("records").setType("record").setMode("REPEATED").setFields(Lists.newArrayList(new TableFieldSchema()
          .setName("name").setType("string"), new TableFieldSchema()
          .setName("value").setType("string"))),
      new TableFieldSchema()
        .setName("record").setType("record").setFields(Lists.newArrayList(new TableFieldSchema()
          .setName("name").setType("string"), new TableFieldSchema()
          .setName("value").setType("string"))))));
  }

  private static class ClassWithRecord {
    private static class Record{
      public String name;
      public String value;
      public Record(String name, String value){
          this.name=name;
          this.value=value;
      }
    }
      
    @SuppressWarnings("unused")
    Record record;
    List<Record> records;

    @SuppressWarnings("unused")
    public ClassWithRecord(Record record, List<Record> records) {
      this.record = record;
      this.records = records;
    }
  }

  public void testGeneratedJsonForClassExtendingAbstractClass() {
    BigQueryDataMarshallerTester<ClassExtendingAbstract> tester =
        new BigQueryDataMarshallerTester<>(
            new BigQueryMarshallerByType<>(ClassExtendingAbstract.class));
    tester.testGeneratedJson("{\"id\":5,\"name\":\"nameField\",\"value\":6}",
        new ClassExtendingAbstract(5, "nameField", 6));

    tester.testSchema(new TableSchema().setFields(Lists.newArrayList(new TableFieldSchema()
        .setName("id").setType("integer").setMode(BigQueryFieldMode.REQUIRED.getValue()),
        new TableFieldSchema().setName("name").setType("string"), new TableFieldSchema()
            .setName("value").setType("integer").setMode(BigQueryFieldMode.REQUIRED.getValue()))));
  }

  public void testGeneratedJsonForClassWithAnUnsupportedType() throws NoSuchFieldException,
      SecurityException {
    Map<Field, BigqueryFieldMarshaller> marshallers = new HashMap<>();
    marshallers.put(ClassWithUnsupportedType.class.getDeclaredField("ip"),
        new BigqueryFieldMarshaller() {

          @Override
          public Class<?> getSchemaType() {
            return String.class;
          }

          @Override
          public Object getFieldValue(Field field, Object object) {
            field.setAccessible(true);
            try {
              return field.get(object);
            } catch (IllegalArgumentException | IllegalAccessException e) {
              throw new IllegalArgumentException(
                  "Cannot read value of the field " + field.getName());
            }
          }
        });
    marshallers.put(ClassWithUnsupportedType.class.getDeclaredField("blob"),
        new BigqueryFieldMarshaller() {

          @Override
          public Class<?> getSchemaType() {
            return String.class;
          }

          @Override
          public Object getFieldValue(Field field, Object object) {
            return "override";
          }
        });    
    BigQueryDataMarshallerTester<ClassWithUnsupportedType> tester =
        new BigQueryDataMarshallerTester<>(
            new BigQueryMarshallerByType<>(ClassWithUnsupportedType.class, marshallers));
    tester.testGeneratedJson("{\"ip\":\"00000001-0002-0003-0004-000000000005\",\"id\":5,\"blob\":\"override\"}",
        new ClassWithUnsupportedType(UUID.fromString("1-2-3-4-5"), 5, "blob"));

    tester.testSchema(new TableSchema().setFields(Lists.newArrayList(
        new TableFieldSchema().setName("ip").setType("string"), new TableFieldSchema().setName("id")
        .setType("integer").setMode(BigQueryFieldMode.REQUIRED.getValue()),
        new TableFieldSchema().setName("blob").setType("string"))));
  }

  private static class ClassWithUnsupportedType {
    @SuppressWarnings("unused")
    Object blob;
    @SuppressWarnings("unused")
    UUID ip;
    @SuppressWarnings("unused")
    int id;

    public ClassWithUnsupportedType(UUID ip, int id, Object blob) {
      this.ip = ip;
      this.id = id;
      this.blob = blob;
    }
  }

  @SuppressWarnings("unused")
  public void testGeneratedJsonForClassWithFieldTypeObject() {
    try {
      new BigQueryDataMarshallerTester<ClassWithFieldTypeObject>(
          new BigQueryMarshallerByType<ClassWithFieldTypeObject>(ClassWithFieldTypeObject.class));
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Type cannot be marshalled into bigquery schema. " + Object.class.getSimpleName(),
          e.getMessage());
      return;
    }
    fail();
  }

  private static class ClassWithFieldTypeObject {
    @SuppressWarnings("unused")
    int id;
    @SuppressWarnings("unused")
    Object name;

    @SuppressWarnings("unused")
    public ClassWithFieldTypeObject(int id, Object name) {
      this.id = id;
      this.name = name;
    }
  }

  public void testGeneratedJsonForClassWithEnumField() {
    BigQueryDataMarshallerTester<ClassWithEnumField> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(ClassWithEnumField.class));
    tester.testGeneratedJson("{\"id\":5,\"mode\":\"REQUIRED\"}",
        new ClassWithEnumField(5, BigQueryFieldMode.REQUIRED));
  }

  private static class ClassWithEnumField {
    @SuppressWarnings("unused")
    int id;
    @SuppressWarnings("unused")
    BigQueryFieldMode mode;

    public ClassWithEnumField(int id, BigQueryFieldMode mode) {
      this.id = id;
      this.mode = mode;
    }
  }

  public void testGeneratedJsonForClassWithDate() {
    BigQueryDataMarshallerTester<ClassWithDate> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(ClassWithDate.class));
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(1406054209);
    tester.testGeneratedJson("{\"id\":1,\"date\":1406054.209,\"cal\":1406054.209}",
        new ClassWithDate(1, new Date(1406054209), cal));

    tester.testSchema(new TableSchema().setFields(Lists.newArrayList(new TableFieldSchema()
        .setName("id").setMode(BigQueryFieldMode.REQUIRED.getValue()).setType("integer"),
        new TableFieldSchema().setName("date").setType("timestamp"),
        new TableFieldSchema().setName("cal").setType("timestamp"))));
  }

  private static class ClassWithDate {
    @SuppressWarnings("unused")
    int id;
    @SuppressWarnings("unused")
    Date date;
    @SuppressWarnings("unused")
    Calendar cal;

    public ClassWithDate(int id, Date date, Calendar cal) {
      this.id = id;
      this.date = date;
      this.cal = cal;
    }
  }

  public void testGeneratedJsonForClassWithBigNumbers() {
    BigQueryDataMarshallerTester<ClassWithBigNumbers> tester = new BigQueryDataMarshallerTester<>(
        new BigQueryMarshallerByType<>(ClassWithBigNumbers.class));
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(1406054209);
    tester.testGeneratedJson(
        "{\"bigInt\":342438484894389432894389432894289489234,"
        + "\"bigDec\":2385923859023849203489023849023841241234.12398}",
        new ClassWithBigNumbers(new BigInteger("342438484894389432894389432894289489234"),
            new BigDecimal("2385923859023849203489023849023841241234.12398")));

    tester.testSchema(new TableSchema().setFields(Lists.newArrayList(
        new TableFieldSchema().setName("bigInt").setType("string"),
        new TableFieldSchema().setName("bigDec").setType("string"))));
  }

  private static class ClassWithBigNumbers {
    @SuppressWarnings("unused")
    BigInteger bigInt;
    @SuppressWarnings("unused")
    BigDecimal bigDec;

    public ClassWithBigNumbers(BigInteger bigInt, BigDecimal bigDec) {
      this.bigInt = bigInt;
      this.bigDec = bigDec;
    }
  }

  @SuppressWarnings("unused")
  public void testClassWithCyclicReference() {
    try {
      new BigQueryDataMarshallerTester<ClassA>(new BigQueryMarshallerByType<ClassA>(ClassA.class));
    } catch (IllegalArgumentException e) {
      assertEquals(ClassA.class + " contains cyclic reference for the field with type "
          + ClassA.class + ". Hence cannot be resolved into bigquery schema.", e.getMessage());
      return;
    }
    fail();
  }

  private static class ClassA {
    @SuppressWarnings("unused")
    ClassB b;
  }

  private static class ClassB {
    @SuppressWarnings("unused")
    ClassA a;
  }
}