.PHONY: up simulate test down

up:
	docker compose up --build

simulate:
	SPRING_PROFILES_ACTIVE=simulate docker compose up --build

test:
	./mvnw verify

down:
	docker compose down
