package edu.tdse.lab5;


public class Main {
    public static void main(String[] args) throws Exception {

        // Debe existir en src/main/resources/webroot/index.html, etc.
        MicroWeb.staticfiles("webroot/");

        MicroWeb.get("/App/hello", (req, resp) -> "Hello " + req.getValues("name"));

        MicroWeb.get("/App/pi", (req, resp) -> String.valueOf(Math.PI));

        MicroWeb.get("/App/hello", (req, resp) -> "Hello " + req.getValuesOrDefault("name", "world"));

        MicroWeb.start(8080);
    }
}