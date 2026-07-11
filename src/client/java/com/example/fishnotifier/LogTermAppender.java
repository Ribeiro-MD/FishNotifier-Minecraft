package com.example.fishnotifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.function.Consumer;

/**
 * Este mod lê todas as logs da pasta .minecraft, ao instalá-lo está ciente que tudo
 * será lido e processado...TUDO.
 */

//NÃO MODIFIQUES NADA AQUI, SE ALGO AQUI NÃO TIVER CORRETO A LEITURA DAS LOGS PARAM DE FUNCIONAR.
//REPITO, ESTE ARQUIVO TEM QUE PERMANECER INTACTO PARA O BOM FUNCIONAMENTO DO MOD
public final class LogTermAppender extends AbstractAppender {

    private final Consumer<String> callback;

    private LogTermAppender(Consumer<String> callback) {
        super("TermWatchAppender", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
        this.callback = callback; //Leitura das logs
    }

    @Override
    public void append(LogEvent event) {
        try {
            String mensagem = event.getMessage().getFormattedMessage();
            callback.accept(mensagem);
        } catch (Exception ignored) {
        }
    }

    // liga o mod ao sistema Log4j2 (a biblioteca que o Minecraft usa para gerar logs)
    public static void registrar(Consumer<String> callback) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();

        LogTermAppender appender = new LogTermAppender(callback);
        appender.start();

        config.addAppender(appender);

        LoggerConfig rootLoggerConfig = config.getRootLogger();
        rootLoggerConfig.addAppender(appender, null, null);

        context.updateLoggers();
    }
}
