package com.example.discify;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class CustomSoundPlayer {
    private int buffer;
    private int source;

    public void initialize() {
        // Initialize OpenAL
        long device = alcOpenDevice((ByteBuffer) null);
        ALCCapabilities alcCapabilities = ALC.createCapabilities(device);
        long context = alcCreateContext(device, (IntBuffer) null);
        alcMakeContextCurrent(context);
        AL.createCapabilities(alcCapabilities);
    }

    public void play(String filePath) {
        try (MemoryStack stack = stackPush()) {
            // Load the .ogg file
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer sampleRate = stack.mallocInt(1);
            ShortBuffer rawAudioBuffer = STBVorbis.stb_vorbis_decode_filename(filePath, channels, sampleRate);
            int format = channels.get(0) == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

            // Upload the sound data to OpenAL
            buffer = alGenBuffers();
            assert rawAudioBuffer != null;
            alBufferData(buffer, format, rawAudioBuffer, sampleRate.get(0));

            // Create a source and play the sound
            source = alGenSources();
            alSourcei(source, AL_BUFFER, buffer);
            alSourcei(source, AL_LOOPING, AL_TRUE); // Set to loop
            alSourcef(source, AL_GAIN, 1.0f); // Set volume (0.0 to 1.0)
            alSourcePlay(source);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (source != 0) {
            alSourceStop(source);
            alDeleteSources(source);
            alDeleteBuffers(buffer);
        }
    }

    public void cleanup() {
        stop();
        alcDestroyContext(alcGetCurrentContext());
        alcCloseDevice(alcGetContextsDevice(alcGetCurrentContext()));
    }
}
