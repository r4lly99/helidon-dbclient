package com.tilaka.helidon.dbclient.service;

/**
 * @author mohd rully k
 */
import io.helidon.dbclient.DbClient;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * A simple service to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 *
 * Get greeting message for Joe:
 * curl -X GET http://localhost:8080/greet/Joe
 *
 * Change greeting
 * curl -X PUT http://localhost:8080/greet/greeting/Hola
 *
 * The message is returned as a JSON object
 */

public class PokemonService extends AbstractPokemonService {

    public PokemonService(DbClient dbClient) {
        super(dbClient);
    }

    /**
     * Delete all pokemons.
     *
     * @param request  the server request
     * @param response the server response
     */
    @Override
    protected void deleteAllPokemons(ServerRequest request, ServerResponse response) {
        dbClient().execute(exec -> exec
                .createNamedDelete("delete-all")
                .execute())
                .thenAccept(count -> response.send("Deleted: " + count + " values"))
                .exceptionally(throwable -> sendError(throwable, response));
    }
}
