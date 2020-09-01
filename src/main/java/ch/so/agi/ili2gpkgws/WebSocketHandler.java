package ch.so.agi.ili2gpkgws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxReader;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.base.Ili2dbException;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2gpkg.GpkgMain;
import ch.interlis.iom_j.itf.ItfReader;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.StartBasketEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WebSocketHandler extends AbstractWebSocketHandler {    
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static String FOLDER_PREFIX = "ili2gpkg_";

    private static String LOG_ENDPOINT = "log";

    @Value("#{servletContext.contextPath}")
    protected String servletContextPath;
    
//    @Value("${server.port}")
//    protected String serverPort;

//    @Autowired
//    IlivalidatorService ilivalidator;

    HashMap<String, File> sessionFileMap = new HashMap<String, File>();
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException, IoxException {        
        File file = sessionFileMap.get(session.getId());
        
        String filename = message.getPayload();
        
        Path copiedFile = Paths.get(file.getParent(), filename);
        Files.copy(file.toPath(), copiedFile, StandardCopyOption.REPLACE_EXISTING);
        log.info(copiedFile.toFile().getAbsolutePath());

        session.sendMessage(new TextMessage("Received: " + filename));        
        session.sendMessage(new TextMessage("Importing..."));

        String logFilename = copiedFile.toFile().getAbsolutePath() + ".log";
        log.info(logFilename);
        
        Config settings = createConfig();
        settings.setFunction(Config.FC_IMPORT);
        settings.setDoImplicitSchemaImport(true);

        String modelName = getModelNameFromTransferFile(copiedFile.toFile().getAbsolutePath());
        settings.setModels(modelName);

        // Hardcodiert für altes Naturgefahrenkarten-Modell, damit
        // nicht eine Koordinatenystemoption im GUI exponiert werden
        // muss. Mit LV03 wollen wir nichts mehr am Hut haben.            
        if (modelName.equalsIgnoreCase("Naturgefahrenkarte_SO_V11")) {
            settings.setDefaultSrsCode("21781");
        } else {
            settings.setDefaultSrsCode("2056");
        }

        settings.setStrokeArcs(settings, settings.STROKE_ARCS_ENABLE);
        settings.setNameOptimization(settings.NAME_OPTIMIZATION_TOPIC);
        settings.setValidation(false);
        
        if (Ili2db.isItfFilename(copiedFile.toFile().getName())) {
            settings.setItfTransferfile(true);
        }
        
        String gpkgFileName = copiedFile.toFile().getAbsolutePath().substring(0, copiedFile.toFile().getAbsolutePath().length()-4) + ".gpkg";
        settings.setDbfile(gpkgFileName);

        settings.setDburl("jdbc:sqlite:" + settings.getDbfile());
        settings.setXtffile(copiedFile.toFile().getAbsolutePath());

        try {
			Ili2db.run(settings, null);
		} catch (Ili2dbException e) {
			e.printStackTrace();
			
			// TODO: send error message
			
			sessionFileMap.remove(session.getId());
		}

        
        // There is no option for config file support in the GUI at the moment.
//        String configFile = "on";

        // Run the validation.
//        String allObjectsAccessible = "true";
//        boolean valid;
//        try {
//            session.sendMessage(new TextMessage("Validating..."));
//            valid = ilivalidator.validate(allObjectsAccessible, configFile, copiedFile.toFile().getAbsolutePath(), logFilename);
//        } catch (IoxException | IOException e) {
//            e.printStackTrace();            
//            log.error(e.getMessage());
//            
//            TextMessage errorMessage = new TextMessage("An error occured while validating the data:" + e.getMessage());
//            session.sendMessage(errorMessage);
//            
//            return;
//        }
//
//        String resultText = "<span style='background-color:#58D68D;'>...validation done:</span>";
//        if (!valid) {
//            resultText = "<span style='background-color:#EC7063'>...validation failed:</span>";
//        }
        
        log.info(servletContextPath);
        log.info(session.getUri().getScheme());
        log.info(session.getUri().getHost());
        
        String schema = session.getUri().getScheme().equalsIgnoreCase("wss") ? "https" : "http";
        String host = session.getUri().getHost();
        
//        String port;
//        if (serverPort.equalsIgnoreCase("80") || serverPort.equalsIgnoreCase("443") || serverPort.equalsIgnoreCase("") || serverPort == null) {
//            port = "";
//        } else if (host.contains("so.ch")) { 
//            // FIXME: Am liebsten wäre es mir, wenn es mit relativen URL gehen würde. Da hatte ich aber Probleme im Browser/Client. Die haben nicht funktioniert in 
//            // der GDI-Umgebung.
//            // Variante: Absolute URL im Client zusammenstöpseln. Ob das aber für die Tests funktioniert, muss man schauen...
//            port = "";
//        } else {
//            port = ":"+serverPort;
//        }
//        log.info(port);
//        
//        String logFileId = copiedFile.getParent().getFileName().toString();
//        TextMessage resultMessage = new TextMessage(resultText + " <a href='"+schema+"://"+host+port+"/"+servletContextPath+"/"+LOG_ENDPOINT+"/"+logFileId+"/"+filename+".log' target='_blank'>Download log file.</a><br/><br/>   ");
//      session.sendMessage(resultMessage);
        
        byte[] fileContent = Files.readAllBytes(new File(gpkgFileName).toPath());
        session.sendMessage(new BinaryMessage(fileContent));
        
        sessionFileMap.remove(session.getId());
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        Path tmpDirectory = Files.createTempDirectory(FOLDER_PREFIX);
        
        // Der Dateinamen ist nicht in der binary message greifbar. Aus diesem Grund
        // wird zuerst die binary message als 'data.file' gespeichert und anschliessend
        // wieder in umbenannt. Der Originalnamen wird als text message geschickt.
        Path uploadFilePath = Paths.get(tmpDirectory.toString(), "data.file"); 
                
        FileChannel fc = new FileOutputStream(uploadFilePath.toFile().getAbsoluteFile(), false).getChannel();
        fc.write(message.getPayload());
        fc.close();

        File file = uploadFilePath.toFile();
        
        sessionFileMap.put(session.getId(), file);
    }
    
    private Config createConfig() {
        Config settings = new Config();
        new GpkgMain().initConfig(settings);
        return settings;
    }
    
    private String getModelNameFromTransferFile(String transferFileName) throws IoxException {
        String model = null;
        String ext = getExtensionByString(transferFileName).orElseThrow(IoxException::new);
        
        IoxReader ioxReader = null;

        try {
            File transferFile = new File(transferFileName);

            if (ext.equalsIgnoreCase("itf")) {
                ioxReader = new ItfReader(transferFile);
            } else {
                ioxReader = new XtfReader(transferFile);
            }

            IoxEvent event;
            StartBasketEvent be = null;
            do {
                event = ioxReader.read();
                if (event instanceof StartBasketEvent) {
                    be = (StartBasketEvent) event;
                    break;
                }
            } while (!(event instanceof EndTransferEvent));

            ioxReader.close();
            ioxReader = null;

            if (be == null) {
                throw new IllegalArgumentException("no baskets in transfer-file");
            }

            String namev[] = be.getType().split("\\.");
            model = namev[0];

        } catch (IoxException e) {
            log.error(e.getMessage());
            e.printStackTrace();
            throw new IoxException("could not parse file: " + new File(transferFileName).getName());
        } finally {
            if (ioxReader != null) {
                try {
                    ioxReader.close();
                } catch (IoxException e) {
                    log.error(e.getMessage());
                    e.printStackTrace();
                    throw new IoxException(
                            "could not close interlis transfer file: " + new File(transferFileName).getName());
                }
                ioxReader = null;
            }
        }
        return model;
    } 
    
    private Optional<String> getExtensionByString(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }
}