package org.mitre.busylightapi;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.json.JSONObject;
import org.mitre.busylightapi.BusyLightAPI.BLColor;
import org.mitre.busylightapi.BusyLightAPI.Product;
import org.mitre.busylightapi.BusyLightAPI.Vendor;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class BusyLightAD extends Application {

	private GridPane gridPane;
	private PasswordField pfURL;
	private Text tStatus;
	private ComboBox<String> cbVendor, cbProduct;
	private BusyLightAPI light;
	private Timeline stopTimeline, pollTimeline, blinkTimeline;
	private Button buttonTest, buttonReg,buttonStop, buttonExit, buttonPaste;
	private Text tConnectStatus; 
	private Client client;
	private String currentJson;
	private Circle circle;
	private boolean circleBlinkOn = false;
	private Color circleBlinkColor;

	private static Clipboard clipboard = Clipboard.getSystemClipboard();
	private static final int BWIDTH = 305;
	private static final int BHEIGHT = 335;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {		

		currentJson = "";
		stage.getIcons().add(new Image("ace.png"));
		//client = ClientBuilder.newClient();
		client = initHttpsClient();

		tStatus = new Text("");

		gridPane = new GridPane();
		gridPane.setHgap(5);
		gridPane.setVgap(5);
		gridPane.setPadding(new Insets(10, 10, 10, 10));

		int row = 0;

		gridPane.add(new Text("Status:"),  0, row);
		tConnectStatus = new Text("disconnected");
		tConnectStatus.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
		tConnectStatus.setFill(javafx.scene.paint.Color.GRAY);
		gridPane.add(tConnectStatus,  1, row);

		row++;
		gridPane.add(new Text("Vendor:"),  0, row);

		String[] vendors = new String[Vendor.values().length];
		for (int i=0; i < Vendor.values().length; i++) {
			vendors[i] = Vendor.values()[i].toString();
		}
		ObservableList<String> options = FXCollections.observableArrayList(vendors);
		cbVendor = new ComboBox<String>(options);
		cbVendor.getSelectionModel().selectFirst();
		gridPane.add(cbVendor ,  1, row);

		row++;
		gridPane.add(new Text("Product:"),  0, row);

		String[] products = new String[Product.values().length];
		for (int i=0; i < Product.values().length; i++) {
			products[i] = Product.values()[i].toString();
		}
		options = FXCollections.observableArrayList(products);
		cbProduct = new ComboBox<String>(options);
		cbProduct.getSelectionModel().selectFirst();
		gridPane.add(cbProduct ,  1, row);		

		row++;
		gridPane.add(new Text("URL:"),  0, row);
		pfURL = new PasswordField();
		pfURL.setPrefColumnCount(50);
		pfURL.setText("https://localhost:1234/getagentstatus/abc123xyz"); //HERE remove later
		gridPane.add(pfURL,  1, row);

		//stage.getIcons().add(new Image("ace.png"));
		//Image image = new Image(getClass().getResourceAsStream("paste.png"));
		buttonPaste = new Button("");
		buttonPaste.setGraphic(new ImageView(new Image("paste.png")));
		buttonPaste.setOnAction(e -> {
			pfURL.setText(clipboard.getString());
		});
		gridPane.add(buttonPaste, 2, row);

		row++;
		buttonReg = new Button("Register");
		buttonReg.setOnAction(e -> {
			tStatus.setText("");
			currentJson = "";
			buttonReg.setDisable(true);
			pfURL.setDisable(true);
			buttonTest.setDisable(true);
			buttonPaste.setDisable(true);
			cbVendor.setDisable(true);
			cbProduct.setDisable(true);
			pfURL.setDisable(true);			

			//send a test request
			WebTarget target = client.target(getGETUri());
			boolean bRegistered = true;
			try {
				tStatus.setText("");
				Response response = target.request().accept("application/json; charset=utf-8").get();
				String json = response.readEntity(String.class);

				if (response.getStatus() != 200) {
					bRegistered = false;
					if (response.getStatus() == 401) {
						System.out.println("in here");
						tStatus.setFill(javafx.scene.paint.Color.RED);
						tStatus.setText("invalid token");
						tConnectStatus.setText("disconnected");
						tConnectStatus.setFill(javafx.scene.paint.Color.GRAY);						
					}
				} else {
					tConnectStatus.setText("connected");
					tConnectStatus.setFill(javafx.scene.paint.Color.GREEN);						
					if (!json.equalsIgnoreCase(currentJson)) {
						processResponse(json);
						currentJson = json;
					}					
					bRegistered = true;
				}
				response.close();			
			} catch (Exception e1) {
				bRegistered = false;
				tStatus.setFill(javafx.scene.paint.Color.RED);
				tStatus.setText("cannot reach server");
				tConnectStatus.setText("disconnected");
				tConnectStatus.setFill(javafx.scene.paint.Color.GRAY);				
			}

			//if registration is good, start the polling
			if (bRegistered) {
				tConnectStatus.setText("connected");
				tConnectStatus.setFill(javafx.scene.paint.Color.GREEN);
				pollTimeline.setCycleCount(Timeline.INDEFINITE);
				pollTimeline.play();
			} else {
				buttonReg.setDisable(false);
				pfURL.setDisable(false);
				buttonTest.setDisable(false);
				buttonPaste.setDisable(false);
				cbVendor.setDisable(false);
				cbProduct.setDisable(false);
				pfURL.setDisable(false);				
			}

		});	
		GridPane gridPane2 = new GridPane();
		gridPane2.setHgap(5);
		gridPane2.setVgap(5);
		gridPane2.add(buttonReg,  0, 0);

		buttonStop = new Button("Stop");
		buttonStop.setOnAction(e -> {
			tStatus.setText("");
			tConnectStatus.setText("disconnected");
			tConnectStatus.setFill(javafx.scene.paint.Color.GRAY);
			buttonReg.setDisable(false);
			pfURL.setDisable(false);
			buttonTest.setDisable(false);
			buttonPaste.setDisable(false);
			cbVendor.setDisable(false);
			cbProduct.setDisable(false);
			pfURL.setDisable(false);	
			circle.setFill(Color.GRAY);
			if (pollTimeline != null) {
				pollTimeline.stop();
			}
			if (blinkTimeline != null) {
				blinkTimeline.stop();
				circleBlinkOn = false;
			}	
			if (stopTimeline != null) {
				stopTimeline.stop();
			}			
			if (light != null) {
				light.stop();
				light.shutdown();
			}
			currentJson = "";
		});	
		gridPane2.add(buttonStop,  1, 0);		

		//initialization of light
		light = new BusyLightAPI();

		//auto-detect Busy Light
		detectBusylight();			
		light.initDevice(Vendor.valueOf(cbVendor.getSelectionModel().getSelectedItem()) , Product.valueOf(cbProduct.getSelectionModel().getSelectedItem()) , null);
		light.setVolume((short)0);

		stopTimeline = new Timeline(new KeyFrame(Duration.millis(1800), new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (light != null) {
					light.stop();
					light.shutdown();
				}			

				if (blinkTimeline != null) {
					blinkTimeline.stop();
				}
				circle.setFill(Color.GRAY);
				
				buttonExit.setDisable(false);
				buttonReg.setDisable(false);
				pfURL.setDisable(false);
				buttonStop.setDisable(false);
				buttonTest.setDisable(false);
				buttonPaste.setDisable(false);

			}
		}));		

		buttonTest = new Button("Test");
		buttonTest.setOnAction(e -> {
			tStatus.setText("");
			detectBusylight();			
			if (light != null) {
				light.stop();
				light.shutdown();
			}

			light = new BusyLightAPI();
			light.initDevice(Vendor.valueOf(cbVendor.getSelectionModel().getSelectedItem()) , Product.valueOf(cbProduct.getSelectionModel().getSelectedItem()) , null);
			light.blinkColor(BLColor.GREEN, 5, 1);
			
			blinkTimeline.stop();
			circleBlinkColor = Color.rgb(0,255,0);
			circleBlinkOn = false;
			blinkTimeline.play();		

			//stopTimeline.setCycleCount(Timeline.INDEFINITE);
			stopTimeline.setCycleCount(1);
			buttonExit.setDisable(true);
			buttonReg.setDisable(true);
			pfURL.setDisable(true);
			buttonStop.setDisable(true);
			buttonTest.setDisable(true);	
			buttonPaste.setDisable(true);
			stopTimeline.play();			

		});	
		gridPane2.add(buttonTest,  2, 0);		

		buttonExit = new Button("Exit");
		buttonExit.setTextFill(Color.RED);
		buttonExit.setOnAction(e -> {
			tStatus.setText("");
			shutdown();
		});	
		gridPane2.add(buttonExit,  3, 0);		

		gridPane.add(gridPane2,  1, row);		

		row++;
		gridPane.add(tStatus,  1, row);

		row++;
		circle = new Circle(0, 0, 75);
		circle.setFill(Color.GRAY);
		circle.setStroke(Color.BLACK);
		circle.setStrokeWidth(5);
		gridPane.add(circle, 1, row);

		pollTimeline = new Timeline(new KeyFrame(Duration.millis(1000), new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				try {
					WebTarget target = client.target(getGETUri());
					Response response = target.request().accept("application/json; charset=utf-8").get();
					if (response.getStatus() != 200) {
						if (response.getStatus() == 401) {
							tStatus.setFill(javafx.scene.paint.Color.RED);
							tConnectStatus.setText("disconnected");
							tConnectStatus.setFill(javafx.scene.paint.Color.GRAY);							
							tStatus.setText("invalid token");
							//System.err.println("invalid token");
						}
					} else {
						tConnectStatus.setText("connected");
						tConnectStatus.setFill(javafx.scene.paint.Color.GREEN);							
						String json = response.readEntity(String.class);
						if (!json.equalsIgnoreCase(currentJson)) {
							processResponse(json);
							currentJson = json;
							//System.out.println("new response: " + json);
						} else {
							//System.out.println("same response");
						}						
						if (!json.equalsIgnoreCase(currentJson)) {
							processResponse(json);
							currentJson = json;
						}
					}					
					response.close();

					//System.out.println("Sent GET request...");
				} catch (ProcessingException e1) {
					//System.err.println("lost remote connection");
					light.stop();
					tStatus.setFill(javafx.scene.paint.Color.RED);
					tStatus.setText("lost remote connection");
					tConnectStatus.setText("disconnected");
					tConnectStatus.setFill(javafx.scene.paint.Color.GRAY);					
				}
			}
		}));		

		blinkTimeline = new Timeline(new KeyFrame(Duration.millis(300), new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (circleBlinkOn) {
					circle.setFill(Color.GRAY);
				} else {
					circle.setFill(circleBlinkColor);
				}
				circleBlinkOn = !circleBlinkOn;
			}
		}));
		blinkTimeline.setCycleCount(Timeline.INDEFINITE);

		//set the scene
		Scene scene = new Scene(gridPane,BWIDTH,BHEIGHT); 
		stage.setTitle("BusyLight - ACE Direct");
		stage.setScene(scene);
		stage.setOnCloseRequest(e -> {
			shutdown();
		});
		stage.show();
	}

	public void processResponse(String json) {

		if (light != null) {
			detectBusylight();			
			light.stop();
			light.shutdown();

			light = new BusyLightAPI();
			light.initDevice(Vendor.valueOf(cbVendor.getSelectionModel().getSelectedItem()) , Product.valueOf(cbProduct.getSelectionModel().getSelectedItem()) , null);
		}

		try {
			JSONObject o = new JSONObject(json);
			if (o.getBoolean("stop")) {
				light.stop();
				blinkTimeline.stop();
				circle.setFill(Color.GRAY);
				circleBlinkOn = false;
			} else {
				int r = o.getInt("r");
				int g = o.getInt("g");
				int b = o.getInt("b");
				if (o.getBoolean("blink")) {
					//blink
					light.blinkColor(r,g,b, 5, 1);
					if (blinkTimeline != null) {
						blinkTimeline.stop();
						circleBlinkOn = false;
						circleBlinkColor = Color.rgb(r,g,b);
						blinkTimeline.play();
					}
				} else {
					//solid
					light.steadyColor(r,g,b);
					circle.setFill(Color.rgb(r,g,b));
				}
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}		
	}

	public void detectBusylight() {
		//try to auto-detect Busy Light
		int[] ret = light.detectBusyLight();
		if (ret[0] != -1 && ret[1] != -1) {
			tStatus.setFill(javafx.scene.paint.Color.BLUE);
			tStatus.setText("BusyLight detected");
			cbVendor.getSelectionModel().select(ret[0]);
			cbProduct.getSelectionModel().select(ret[1]);
		}			
	}

	public void shutdown() {
		if (light != null) {
			light.stop();
			light.shutdown();
			light = null;
		}
		if (pollTimeline != null) {
			pollTimeline.stop();
		}
		if (blinkTimeline != null) {
			blinkTimeline.stop();
			circleBlinkOn = false;
		}		
		if (stopTimeline != null) {
			stopTimeline.stop();
		}
		Platform.exit();		
	}

	public String getGETUri() {
		String url = pfURL.getText();
		if (url.endsWith("/"))
			url = url.substring(0, url.length()-1);
		pfURL.setText(url);
		return url;
	}

	//Makes this an HTTPS client
	public Client initHttpsClient() throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext ctx = SSLContext.getInstance("SSL");
		ctx.init(null, certs, new SecureRandom());

		return ClientBuilder.newBuilder().hostnameVerifier(new TrustAllHostNameVerifier()).sslContext(ctx).build();		
	}

	//Support for HTTPS
	TrustManager[] certs = new TrustManager[]{
			new X509TrustManager() {
				@Override
				public X509Certificate[] getAcceptedIssuers() {return null;}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
			}
	};

	//Support for HTTPS
	public static class TrustAllHostNameVerifier implements HostnameVerifier {
		public boolean verify(String hostname, SSLSession session) {return true;}
	}	





}