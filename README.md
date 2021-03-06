A port of Scala Futures to Java+Android

See http://docs.scala-lang.org/overviews/core/futures.html

Usage:

SBT:
`libraryDependencies += "com.hanhuy.android" % "java-futures" % "0.3"`

Maven:
```
  <dependency>
    <groupId>com.hanhuy.android</groupId>
    <artifactId>java-futures</artifactId>
    <version>0.3</version>
  </dependency>
```

Gradle:
`compile 'com.hanhuy.android:java-futures:0.3'`

```
import com.hanhuy.android.concurrent.Future
import com.hanhuy.android.concurrent.Promise
```

etc.


An example of the power of composing Futures:
```
Future<T> fetchObject(String url, Class<T> type) {
    return requestJson(url).recover(
        fromFile(safeNameFor(url))).map(
            saveAndParseJson(safeNameFor(url), type));
}
```

The above does the following:

1. Fetch JSON from URL in the background
   * If that fails then load the JSON from a File in the background
2. Converts the JSON into an object of type T in the background
   * also saves the JSON into a file

All of this is chained automatically upon completion of background tasks
_Note_: The above are all examples of methods not included in this library
