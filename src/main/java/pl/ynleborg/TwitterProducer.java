package pl.ynleborg;

import com.google.common.collect.Lists;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TwitterProducer {

    private static Logger logger = LoggerFactory.getLogger(TwitterProducer.class.getName());

    private List<String> terms = Lists.newArrayList("witcher", "Witcher", "Wiedźmin", "wiedźmin", "wiedzmin");

    public static void main(String[] args) throws InterruptedException {
        Properties prop = new Properties();
        try (InputStream input = TwitterProducer.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                logger.error("Sorry, unable to find config.properties");
                return;
            }
            prop.load(input);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        new TwitterProducer().run(prop);
    }

    private void run(Properties prop) throws InterruptedException {
        MongoClient mongoClient = null;
        Client client = null;
        try {
            logger.info("START");
            BlockingQueue<String> msgQueue = new LinkedBlockingQueue<>(1000);
            client = createTwitterClient(msgQueue, prop);
            client.connect();

            String connectionString = "mongodb+srv://"
                    + prop.getProperty("mongo.user")
                    + ":"
                    + prop.getProperty("mongo.password")
                    + "@przydancluster-ewm2q.mongodb.net/test?retryWrites=true&w=majority";
            mongoClient = MongoClients.create(
                    connectionString);
            MongoDatabase database = mongoClient.getDatabase("PrzydandemoDB");
            MongoCollection<Document> tweets = database.getCollection("tweets");

            while (!client.isDone()) {
                String msg = msgQueue.poll(5, TimeUnit.SECONDS);
                if (msg != null) {
                    logger.info(msg);
                    tweets.insertOne(Document.parse(msg));
                }
            }
        } finally {
            if (mongoClient != null) {
                mongoClient.close();
            }
            if (client != null) {
                client.stop();
            }
        }
        logger.info("STOP");
    }

    private Client createTwitterClient(BlockingQueue<String> msgQueue, Properties prop) {

        Hosts streamHost = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint endpoint = new StatusesFilterEndpoint();
        endpoint.trackTerms(terms);
        Authentication auth = new OAuth1(
                prop.getProperty("consumerKey"),
                prop.getProperty("consumerSecret"),
                prop.getProperty("token"),
                prop.getProperty("secret"));

        ClientBuilder builder = new ClientBuilder()
                .name("PrzydandemoDB")
                .hosts(streamHost)
                .authentication(auth)
                .endpoint(endpoint)
                .processor(new StringDelimitedProcessor(msgQueue));

        return builder.build();
    }
}