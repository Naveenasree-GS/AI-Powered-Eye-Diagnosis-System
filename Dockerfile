FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy project files
COPY . /app

# Compile the Java server
RUN javac EyeCareServer.java

# Expose the default port
EXPOSE 8000

# Ensure PORT env is available (Render sets this automatically)
ENV PORT=8000

# Start the server
CMD ["java", "EyeCareServer"]
