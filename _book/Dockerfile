FROM openjdk:8-alpine

COPY target/uberjar/coronavirus-charts.jar /coronavirus-charts/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/coronavirus-charts/app.jar"]
