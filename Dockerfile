FROM hseeberger/scala-sbt:latest

COPY . /app
WORKDIR /app

RUN sbt clean && \
  sbt compile && \
  sbt stage

ENV PORT 80
EXPOSE 80

CMD ["./target/universal/stage/bin/boilerpipe-api"]
