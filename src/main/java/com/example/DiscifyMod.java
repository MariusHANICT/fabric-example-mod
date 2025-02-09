package com.example;

import com.example.discify.CustomSoundPlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.text.Text;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class DiscifyMod implements ModInitializer {

    private CustomSoundPlayer soundPlayer;

    public static final String AUDIO_PATH = "mods/audio";
    
    @Override
    public void onInitialize() {
        soundPlayer = new CustomSoundPlayer();
        soundPlayer.initialize();

        // Register cleanup on game close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> soundPlayer.cleanup()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("discify")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                            .then(CommandManager.argument("youtubeUrl", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        String youtubeUrl = StringArgumentType.getString(context, "youtubeUrl");
                                        String songName = StringArgumentType.getString(context, "name");
                                        try {
                                            File outputDir = new File(AUDIO_PATH);
                                            if (!outputDir.exists()) {
                                                outputDir.mkdirs();
                                            }
                                            String output = downloadAudio(youtubeUrl, outputDir, songName);
                                            context.getSource().sendMessage(Text.of("Downloaded audio: " + output));
                                        } catch (Exception e) {
                                            context.getSource().sendError(Text.of("Failed to download audio: " + e.getMessage()));
                                        }
                                        return 1;
                                    })
                            )
                    )
                    .then(CommandManager.literal("play")
                            .then(CommandManager.argument("songName", StringArgumentType.string())
                                    .executes(context -> {
                                        context.getSource().sendMessage(Text.of("Play command executed."));
                                        String songName = StringArgumentType.getString(context, "songName");
                                        String filePath = AUDIO_PATH + "/" + songName + ".ogg"; // Path to the .ogg file
                                        context.getSource().sendMessage(Text.of("Playing now."));
                                        soundPlayer.play(filePath);

                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    private String downloadAudio(String youtubeUrl, File outputDir, String songName) throws Exception {
        // Step 1: Create the ffmpeg directory
        File ffmpegDir = new File(outputDir, "ffmpeg");
        if (!ffmpegDir.exists()) {
            ffmpegDir.mkdirs();
        } else {
            FileUtils.cleanDirectory(ffmpegDir); // Requires Apache Commons IO
        }

        // Step 2: Extract yt-dlp, ffmpeg, and ffprobe into the ffmpeg directory
        extractExecutable("yt-dlp.exe", new File(ffmpegDir, "yt-dlp.exe"));
        extractExecutable("ffmpeg.exe", new File(ffmpegDir, "ffmpeg.exe"));
        extractExecutable("ffprobe.exe", new File(ffmpegDir, "ffprobe.exe"));

        Process process = getProcess(youtubeUrl, songName, ffmpegDir);

        // Step 4: Read the output
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        // Step 5: Wait for the process to complete
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("yt-dlp failed with output:\n" + output);
        }

        FileUtils.deleteDirectory(ffmpegDir); // Requires Apache Commons IO

        return "mods/audio/" + songName + ".ogg";
    }

    private static @NotNull Process getProcess(String youtubeUrl, String songName, File ffmpegDir) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                new File(ffmpegDir, "yt-dlp.exe").getAbsolutePath(),
                "-x", "--audio-format", "vorbis", // Change to OGG (Vorbis codec)
                "--ffmpeg-location", ffmpegDir.getAbsolutePath(),
                "-o", new File(AUDIO_PATH, songName).getAbsolutePath(),
                youtubeUrl
        );

        processBuilder.redirectErrorStream(true); // Combine stdout and stderr
        return processBuilder.start();
    }

    private void extractExecutable(String resourceName, File outputFile) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new RuntimeException(resourceName + " not found in resources");
        }

        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            inputStream.transferTo(outputStream);
        }

        System.out.println("Extracted " + resourceName + " to: " + outputFile.getAbsolutePath());
    }
}
