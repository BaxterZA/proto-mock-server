# Simple protobuf mock server

1.Save "real" response body from Charles to file

![Save real response from Charles to file](https://raw.githubusercontent.com/BaxterZA/proto-mock-server/master/Save%20real%20response%20from%20Charles%20to%20file.png)

2.Turn on **Map Remote** tool in Charles

![Add Map Remote Settings](https://github.com/BaxterZA/proto-mock-server/raw/master/Add%20Map%20Remote%20Settings.png)

3.Start local server

```java -jar mock.jar -p <port> (optional, default 8080) -m <Protobuf message full type name> -f <file> -d <Descriptor file 1> .. <Descriptor file N>```

Example:

``` java -jar ./out/artifacts/mock_jar/mock.jar -m bidmachine.protobuf.openrtb.Openrtb -f bidmachine.protobuf.openrtb.Openrtb.json -d bidmachine.desc google.desc ```
