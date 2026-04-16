# convert_audio.py
try:
    from pydub import AudioSegment
    print("Converting space_audio.mp3 to space_audio.wav...")
    audio = AudioSegment.from_mp3("space_audio.mp3")
    audio.export("space_audio.wav", format="wav")
    print("Conversion complete! Created space_audio.wav")
except ImportError:
    print("Installing pydub...")
    import subprocess
    import sys
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pydub"])
    print("Please run the script again")
except Exception as e:
    print(f"Error: {e}")
    print("Alternative: Use Audacity or online converter to convert MP3 to WAV")
