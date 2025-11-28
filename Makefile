.PHONY: setup up down build test clean

setup:
docker-compose pull

up:
docker-compose up --build

down:
docker-compose down -v

build:
mvn -DskipTests package

test:
mvn test

clean:
docker-compose down -v || true
rm -rf target
