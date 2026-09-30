package com.brokers.channel.rentalcars.auth;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record RcTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
