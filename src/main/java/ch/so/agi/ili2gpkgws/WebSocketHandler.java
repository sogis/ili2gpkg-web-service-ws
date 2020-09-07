package ch.so.agi.ili2gpkgws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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
    
    @Autowired
    private ResourceLoader resourceLoader;
    
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

        String modelName = null;
        try {
            modelName = getModelNameFromTransferFile(copiedFile.toFile().getAbsolutePath());
            settings.setModels(modelName);
        } catch (IoxException e) {
			e.printStackTrace();
			session.sendMessage(new TextMessage("<span style='background-color:#EC7063;'>...import failed:</span> " + e.getMessage()));
			sessionFileMap.remove(session.getId());
			return;
        }

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
        settings.setCreateEnumDefs(Config.CREATE_ENUM_DEFS_MULTI);
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
			session.sendMessage(new TextMessage("<span style='background-color:#58D68D;'>...import failed.</span>"));
			sessionFileMap.remove(session.getId());
			return;
		}

        // Kopieren des vordefinierten QGIS-Projekt in die GeoPackage-Datei.
        Resource resource = resourceLoader.getResource("classpath:wmtsortho.qgz");
        InputStream inputStream = resource.getInputStream();
        File qgzFile = new File(copiedFile.toFile().getParent(), resource.getFilename());
        log.info(qgzFile.getAbsolutePath());
        Files.copy(inputStream, qgzFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();

        String url = "jdbc:sqlite:" + settings.getDbfile();
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
        	stmt.execute("CREATE TABLE qgis_projects(name TEXT PRIMARY KEY, metadata BLOB, content BLOB)");
        	
        	// name = datenkontrolle
        	// metadata = {"last_modified_time": "2020-09-03T08:13:20", "last_modified_user": "stefan" }
        	// content = 
        	
        	
        } catch (SQLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        
        
        
        
        
        
        session.sendMessage(new TextMessage("<span style='background-color:#58D68D;'>...import done.</span>"));
        
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