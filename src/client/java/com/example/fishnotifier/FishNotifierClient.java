package com.example.fishnotifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * Este mod lê todas as logs da pasta .minecraft, ao instalá-lo está ciente que tudo
 * será lido e processado...TUDO.
 */
public class FishNotifierClient implements ClientModInitializer {

    private static final TermoConfig TERMO_CARANGUEJO =
            new TermoConfig("LusíadasCraft ➤ Pescaste um Caranguejo!", "🦀", "fishnotifier.overlay.caught", 3, SoundEvents.BLOCK_ANVIL_LAND);

     static final TermoConfig TERMO_MORDIDA =
            new TermoConfig(null, "🐟", "fishnotifier.overlay.generic", 2, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value());

    private static final List<TermoConfig> TERMOS = new CopyOnWriteArrayList<>(List.of(
            TERMO_CARANGUEJO
    ));


    private record TermoConfig(String termo, String icone, String chaveSubtitulo, int numeroDeToques, SoundEvent som) {}

    // Intervalo entre cada toque em milissegundos (1 segundo = 1000 milissegundos).
    private static final long INTERVALO_MS = 500;

    // Tempo de estágio (em ticks (20 ticks = 1 segundo))
    private static final int DURACAO_FADE_IN = 5;
    private static final int DURACAO_ESTADIA = 40;
    private static final int DURACAO_FADE_OUT = 10;

    // Se a bóia descer mais depressa do que isto (Y negativo) não é naturaç = é mordida no anzol
    private static final double LIMIAR_VELOCIDADE_MORDIDA = -0.2;

    // Volume do som (1.0 = normal, podes subir para 2.0, 3.0 para ficar bem mais alto, o normal é 1.0f)
    private static final float VOLUME = 2.0f;

    private static final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "termwatch-agendador");
        t.setDaemon(true);
        return t;
    });

    // Guardam o estado da bóia entre ticks, para só notificar uma vez
    private static FishingBobberEntity boiaAnterior = null;
    private static boolean jaNotificouEstaMordida = false;
    private static boolean prontaParaDetetarMordida = false;
    private static int ticksParadaSeguidos = 0;

    // Quantos ticks a boia fica em repouso pra começar a contar o timer da ação do /title
    private static final int TICKS_ATE_ARMAR = 15;
    // Quão perto de 0 a velocidade tem de estar para contar como estando a flutuar/em repouso na água.
    private static final double LIMIAR_PARADA = 0.03;

    @Override
    public void onInitializeClient() {
        // Primeira leitura (chat) — só apanha o caranguejo agora
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            verificarTexto(message.getString());
            return true;
        });

        // Segunda leitura (logs)
        LogTermAppender.registrar(FishNotifierClient::verificarTexto);

        // Primeira leitura da cana: observa a bóia da cana de pesca a cada tick
        ClientTickEvents.END_CLIENT_TICK.register(FishNotifierClient::verificarMordida);
    }

    private static void verificarTexto(String texto) {
        if (texto == null) return;
        String textoLower = texto.toLowerCase(Locale.ROOT);
        for (TermoConfig termo : TERMOS) {
            if (textoLower.contains(termo.termo().toLowerCase(Locale.ROOT))) {
                notificar(termo);
                break;
            }
        }
    }

    private static void verificarMordida(MinecraftClient client) {
        if (client.player == null) {
            reiniciarEstado();
            return;
        }

        FishingBobberEntity boia = client.player.fishHook;

        // Sem cana lançada: reinicia o estado, fundalmental para repetir o ciclo
        if (boia == null) {
            reiniciarEstado();
            return;
        }

        // Nova bóia lançada (cana relançada): reinicia o estado.
        if (boia != boiaAnterior) {
            boiaAnterior = boia;
            jaNotificouEstaMordida = false;
            prontaParaDetetarMordida = false;
            ticksParadaSeguidos = 0;
        }

        double velocidadeY = boia.getVelocity().y;

        if (!prontaParaDetetarMordida) {
            // Ainda a "assentar" na água (ignora o splash do lançamento da cana)
            if (Math.abs(velocidadeY) < LIMIAR_PARADA) {
                ticksParadaSeguidos++;
                if (ticksParadaSeguidos >= TICKS_ATE_ARMAR) {
                    prontaParaDetetarMordida = true;
                }
            } else {
                ticksParadaSeguidos = 0;
            }
            return;
        }

        if (!jaNotificouEstaMordida && velocidadeY < LIMIAR_VELOCIDADE_MORDIDA) {
            jaNotificouEstaMordida = true;
            notificar(TERMO_MORDIDA);
        }
    }

    private static void reiniciarEstado() {
        boiaAnterior = null;
        jaNotificouEstaMordida = false;
        prontaParaDetetarMordida = false;
        ticksParadaSeguidos = 0;
    }

    private static void notificar(TermoConfig termo) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // Mostra o título (ícone) e o subtítulo (tipo de pesca)
        client.execute(() -> {
            if (client.inGameHud != null) {
                client.inGameHud.setTitleTicks(DURACAO_FADE_IN, DURACAO_ESTADIA, DURACAO_FADE_OUT);
                client.inGameHud.setTitle(Text.literal(termo.icone()));
                client.inGameHud.setSubtitle(Text.translatable(termo.chaveSubtitulo()));
            }
        });

        //ciclo for pra tocar o sino
        for (int i = 0; i < termo.numeroDeToques(); i++) {
            agendador.schedule(
                    () -> client.execute(() -> tocarSino(termo.som())),
                    i * INTERVALO_MS,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private static void tocarSino(SoundEvent som) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSoundManager() != null && client.player != null) {
            client.getSoundManager().play(
                    new PositionedSoundInstance(
                            som,
                            SoundCategory.MASTER,
                            VOLUME,
                            2.4f,                                 // Quanto maior o número, maior o som. O normal é 1!
                            Random.create(),
                            client.player.getX(), client.player.getY(), client.player.getZ()
                    )
            );
        }
    }
}