package pt.unl.fct.di.adc.firstwebapp.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.adc.firstwebapp.util.AuthToken;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.logging.Logger;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    private static final Logger LOG = Logger.getLogger(AccountResource.class.getName());
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private static final Gson g = new Gson();

    private static final String KIND_USER = "User";
    private static final String KIND_SESSION = "Session";

    // Error codes
    private static final int ERR_INVALID_CREDENTIALS = 9900;
    private static final int ERR_USER_ALREADY_EXISTS = 9901;
    private static final int ERR_USER_NOT_FOUND = 9902;
    private static final int ERR_INVALID_TOKEN = 9903;
    private static final int ERR_TOKEN_EXPIRED = 9904;
    private static final int ERR_UNAUTHORIZED = 9905;
    private static final int ERR_INVALID_INPUT = 9906;
    private static final int ERR_FORBIDDEN = 9907;

    private static final Map<Integer, String> ERROR_MESSAGES = new HashMap<>();
    static {
        ERROR_MESSAGES.put(ERR_INVALID_CREDENTIALS, "The username-password pair is not valid");
        ERROR_MESSAGES.put(ERR_USER_ALREADY_EXISTS, "Error in creating an account because the username already exists");
        ERROR_MESSAGES.put(ERR_USER_NOT_FOUND, "The username referred in the operation doesn't exist in registered accounts");
        ERROR_MESSAGES.put(ERR_INVALID_TOKEN, "The operation is called with an invalid token (wrong format for example)");
        ERROR_MESSAGES.put(ERR_TOKEN_EXPIRED, "The operation is called with a token that is expired");
        ERROR_MESSAGES.put(ERR_UNAUTHORIZED, "The operation is not allowed for the user role");
        ERROR_MESSAGES.put(ERR_INVALID_INPUT, "The call is using input data not following the correct specification");
        ERROR_MESSAGES.put(ERR_FORBIDDEN, "The operation generated a forbidden error by other reason");
    }

    // ======================== HELPERS ========================

    private Response errorResponse(int errorCode) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", String.valueOf(errorCode));
        resp.put("data", ERROR_MESSAGES.get(errorCode));
        return Response.ok(g.toJson(resp), MediaType.APPLICATION_JSON).build();
    }

    private Response successResponse(Object data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "success");
        resp.put("data", data);
        return Response.ok(g.toJson(resp), MediaType.APPLICATION_JSON).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(String body) {
        try { return g.fromJson(body, Map.class); }
        catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getInput(Map<String, Object> request) {
        return request == null ? null : (Map<String, Object>) request.get("input");
    }

    @SuppressWarnings("unchecked")
    private AuthToken getToken(Map<String, Object> request) {
        if (request == null) return null;
        Map<String, Object> tm = (Map<String, Object>) request.get("token");
        if (tm == null) return null;
        AuthToken t = new AuthToken();
        t.tokenId = (String) tm.get("tokenId");
        t.userId = (String) tm.get("userId");
        t.role = (String) tm.get("role");
        if (tm.get("issuedAt") != null) t.issuedAt = ((Number) tm.get("issuedAt")).longValue();
        if (tm.get("expiresAt") != null) t.expiresAt = ((Number) tm.get("expiresAt")).longValue();
        return t;
    }

    private Entity validateToken(AuthToken token, int[] err) {
        if (token == null || token.tokenId == null || token.userId == null) {
            err[0] = ERR_INVALID_TOKEN; return null;
        }
        Key sk = datastore.newKeyFactory().setKind(KIND_SESSION).newKey(token.tokenId);
        Entity session = datastore.get(sk);
        if (session == null) { err[0] = ERR_INVALID_TOKEN; return null; }
        if (!session.getString("userId").equals(token.userId)) { err[0] = ERR_INVALID_TOKEN; return null; }
        if (System.currentTimeMillis() > session.getLong("expiresAt")) {
            datastore.delete(sk); err[0] = ERR_TOKEN_EXPIRED; return null;
        }
        return session;
    }

    private Entity requireValidToken(Map<String, Object> request, int[] err) {
        return validateToken(getToken(request), err);
    }

    private String str(Map<String, Object> m, String k) {
        if (m == null || m.get(k) == null) return null;
        String s = m.get(k).toString().trim();
        return s.isEmpty() ? null : s;
    }

    // ======================== Op1: CREATE ACCOUNT ========================
    @POST
    @Path("/createaccount")
    public Response createAccount(String body) {
        LOG.fine("Op1: CreateAccount");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);
        Map<String, Object> input = getInput(request);
        if (input == null) return errorResponse(ERR_INVALID_INPUT);

        String username = str(input, "username");
        String password = str(input, "password");
        String confirmation = str(input, "confirmation");
        String email = str(input, "email");
        String phone = str(input, "phone");
        String address = str(input, "address");
        String role = str(input, "role");

        if (username == null || password == null || confirmation == null || email == null || role == null)
            return errorResponse(ERR_INVALID_INPUT);
        if (!password.equals(confirmation)) return errorResponse(ERR_INVALID_INPUT);
        if (!role.equals("USER") && !role.equals("BOFFICER") && !role.equals("ADMIN"))
            return errorResponse(ERR_INVALID_INPUT);

        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(username);
            if (txn.get(userKey) != null) { txn.rollback(); return errorResponse(ERR_USER_ALREADY_EXISTS); }

            Entity.Builder b = Entity.newBuilder(userKey)
                    .set("username", username)
                    .set("password", DigestUtils.sha512Hex(password))
                    .set("email", email)
                    .set("role", role)
                    .set("phone", phone != null ? phone : "")
                    .set("address", address != null ? address : "")
                    .set("creationTime", Timestamp.now());
            txn.put(b.build());
            txn.commit();

            LOG.info("Account created: " + username);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("username", username);
            data.put("role", role);
            return successResponse(data);
        } catch (DatastoreException e) {
            LOG.severe("createAccount error: " + e.getMessage());
            return errorResponse(ERR_FORBIDDEN);
        } finally { if (txn.isActive()) txn.rollback(); }
    }

    // ======================== Op2: LOGIN ========================
    @POST
    @Path("/login")
    public Response login(String body) {
        LOG.fine("Op2: Login");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);
        Map<String, Object> input = getInput(request);
        if (input == null) return errorResponse(ERR_INVALID_INPUT);

        String username = str(input, "username");
        String password = str(input, "password");
        if (username == null || password == null) return errorResponse(ERR_INVALID_INPUT);

        Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(username);
        Entity user = datastore.get(userKey);
        if (user == null) return errorResponse(ERR_USER_NOT_FOUND);

        if (!user.getString("password").equals(DigestUtils.sha512Hex(password)))
            return errorResponse(ERR_INVALID_CREDENTIALS);

        String userRole = user.getString("role");
        AuthToken token = new AuthToken(username, userRole);

        Key sessionKey = datastore.newKeyFactory().setKind(KIND_SESSION).newKey(token.tokenId);
        Entity session = Entity.newBuilder(sessionKey)
                .set("tokenId", token.tokenId)
                .set("userId", token.userId)
                .set("role", token.role)
                .set("issuedAt", token.issuedAt)
                .set("expiresAt", token.expiresAt)
                .build();
        datastore.put(session);

        LOG.info("Login successful: " + username);

        Map<String, Object> tokenData = new LinkedHashMap<>();
        tokenData.put("tokenId", token.tokenId);
        tokenData.put("userId", token.userId);
        tokenData.put("role", token.role);
        tokenData.put("issuedAt", token.issuedAt);
        tokenData.put("expiresAt", token.expiresAt);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", tokenData);
        return successResponse(data);
    }

    // ======================== Op3: SHOW USERS ========================
    @POST
    @Path("/showusers")
    public Response showUsers(String body) {
        LOG.fine("Op3: ShowUsers");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        String callerRole = session.getString("role");
        if (!callerRole.equals("ADMIN") && !callerRole.equals("BOFFICER"))
            return errorResponse(ERR_UNAUTHORIZED);

        Query<Entity> query = Query.newEntityQueryBuilder().setKind(KIND_USER).build();
        QueryResults<Entity> results = datastore.run(query);

        List<Map<String, String>> usersList = new ArrayList<>();
        while (results.hasNext()) {
            Entity u = results.next();
            Map<String, String> um = new LinkedHashMap<>();
            um.put("userId", u.getKey().getName());
            um.put("username", u.getString("username"));
            um.put("email", u.getString("email"));
            um.put("role", u.getString("role"));
            usersList.add(um);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", usersList);
        return successResponse(data);
    }

    // ======================== Op4: DELETE ACCOUNT ========================
    @POST
    @Path("/deleteaccount")
    public Response deleteAccount(String body) {
        LOG.fine("Op4: DeleteAccount");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        if (!session.getString("role").equals("ADMIN")) return errorResponse(ERR_UNAUTHORIZED);

        Map<String, Object> input = getInput(request);
        String userId = str(input, "userId");
        if (userId == null) return errorResponse(ERR_INVALID_INPUT);

        Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(userId);
        if (datastore.get(userKey) == null) return errorResponse(ERR_USER_NOT_FOUND);

        // Delete user and clean up their sessions
        datastore.delete(userKey);

        // Clean up sessions for deleted user
        Query<Entity> sq = Query.newEntityQueryBuilder()
                .setKind(KIND_SESSION)
                .setFilter(StructuredQuery.PropertyFilter.eq("userId", userId))
                .build();
        QueryResults<Entity> sessions = datastore.run(sq);
        List<Key> keysToDelete = new ArrayList<>();
        while (sessions.hasNext()) keysToDelete.add(sessions.next().getKey());
        if (!keysToDelete.isEmpty()) datastore.delete(keysToDelete.toArray(new Key[0]));

        LOG.info("Account deleted: " + userId);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("message", "Account deleted successfully");
        return successResponse(data);
    }

    // ======================== Op5: MODIFY ACCOUNT ATTRIBUTES ========================
    @POST
    @Path("/modaccount")
    public Response modifyAccount(String body) {
        LOG.fine("Op5: ModifyAccountAttributes");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        Map<String, Object> input = getInput(request);
        if (input == null) return errorResponse(ERR_INVALID_INPUT);
        String targetUserId = str(input, "userId");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) input.get("attributes");
        if (targetUserId == null || attributes == null || attributes.isEmpty())
            return errorResponse(ERR_INVALID_INPUT);

        // Username cannot be changed (primary key)
        if (attributes.containsKey("username")) return errorResponse(ERR_INVALID_INPUT);

        String callerRole = session.getString("role");
        String callerId = session.getString("userId");

        Key targetKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(targetUserId);
        Entity targetUser = datastore.get(targetKey);
        if (targetUser == null) return errorResponse(ERR_USER_NOT_FOUND);

        String targetRole = targetUser.getString("role");

        // RBAC: USER->own only, BOFFICER->own+USER, ADMIN->any
        if (callerRole.equals("USER") && !callerId.equals(targetUserId))
            return errorResponse(ERR_UNAUTHORIZED);
        if (callerRole.equals("BOFFICER") && !callerId.equals(targetUserId) && !targetRole.equals("USER"))
            return errorResponse(ERR_UNAUTHORIZED);

        Entity.Builder builder = Entity.newBuilder(targetUser);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (!entry.getKey().equals("username") && entry.getValue() != null)
                builder.set(entry.getKey(), entry.getValue().toString());
        }
        datastore.put(builder.build());

        LOG.info("Account modified: " + targetUserId);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("message", "Updated successfully");
        return successResponse(data);
    }

    // ======================== Op6: SHOW AUTHENTICATED SESSIONS ========================
    @POST
    @Path("/showauthsessions")
    public Response showAuthSessions(String body) {
        LOG.fine("Op6: ShowAuthenticatedSessions");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        if (!session.getString("role").equals("ADMIN")) return errorResponse(ERR_UNAUTHORIZED);

        Query<Entity> query = Query.newEntityQueryBuilder().setKind(KIND_SESSION).build();
        QueryResults<Entity> results = datastore.run(query);

        List<Map<String, Object>> sessionsList = new ArrayList<>();
        long now = System.currentTimeMillis();
        while (results.hasNext()) {
            Entity s = results.next();
            long expiresAt = s.getLong("expiresAt");
            if (now > expiresAt) continue;
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("tokenId", s.getString("tokenId"));
            sm.put("userId", s.getString("userId"));
            sm.put("role", s.getString("role"));
            sm.put("expiresAt", expiresAt);
            sessionsList.add(sm);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessions", sessionsList);
        return successResponse(data);
    }

    // ======================== Op7: SHOW USER ROLE ========================
    @POST
    @Path("/showuserrole")
    public Response showUserRole(String body) {
        LOG.fine("Op7: ShowUserRole");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        String callerRole = session.getString("role");
        if (!callerRole.equals("ADMIN") && !callerRole.equals("BOFFICER"))
            return errorResponse(ERR_UNAUTHORIZED);

        Map<String, Object> input = getInput(request);
        String userId = str(input, "userId");
        if (userId == null) return errorResponse(ERR_INVALID_INPUT);

        Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(userId);
        Entity user = datastore.get(userKey);
        if (user == null) return errorResponse(ERR_USER_NOT_FOUND);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("role", user.getString("role"));
        return successResponse(data);
    }

    // ======================== Op8: CHANGE USER ROLE ========================
    @POST
    @Path("/changeuserrole")
    public Response changeUserRole(String body) {
        LOG.fine("Op8: ChangeUserRole");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        if (!session.getString("role").equals("ADMIN")) return errorResponse(ERR_UNAUTHORIZED);

        Map<String, Object> input = getInput(request);
        String userId = str(input, "userId");
        String newRole = str(input, "newRole");
        if (userId == null || newRole == null) return errorResponse(ERR_INVALID_INPUT);
        if (!newRole.equals("USER") && !newRole.equals("BOFFICER") && !newRole.equals("ADMIN"))
            return errorResponse(ERR_INVALID_INPUT);

        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(userId);
            Entity user = txn.get(userKey);
            if (user == null) { txn.rollback(); return errorResponse(ERR_USER_NOT_FOUND); }

            txn.put(Entity.newBuilder(user).set("role", newRole).build());
            txn.commit();

            LOG.info("Role changed: " + userId + " -> " + newRole);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("message", "Role updated successfully");
            return successResponse(data);
        } catch (DatastoreException e) {
            LOG.severe("changeUserRole error: " + e.getMessage());
            return errorResponse(ERR_FORBIDDEN);
        } finally { if (txn.isActive()) txn.rollback(); }
    }

    // ======================== Op9: CHANGE USER PASSWORD ========================
    @POST
    @Path("/changeuserpwd")
    public Response changeUserPassword(String body) {
        LOG.fine("Op9: ChangeUserPassword");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        Map<String, Object> input = getInput(request);
        String userId = str(input, "userId");
        String oldPassword = str(input, "oldPassword");
        String newPassword = str(input, "newPassword");
        if (userId == null || oldPassword == null || newPassword == null)
            return errorResponse(ERR_INVALID_INPUT);

        String callerRole = session.getString("role");
        String callerId = session.getString("userId");

        // USER and BOFFICER: own password only. ADMIN: any.
        if (!callerRole.equals("ADMIN") && !callerId.equals(userId))
            return errorResponse(ERR_UNAUTHORIZED);

        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(userId);
            Entity user = txn.get(userKey);
            if (user == null) { txn.rollback(); return errorResponse(ERR_USER_NOT_FOUND); }

            if (!user.getString("password").equals(DigestUtils.sha512Hex(oldPassword))) {
                txn.rollback();
                return errorResponse(ERR_INVALID_CREDENTIALS);
            }

            txn.put(Entity.newBuilder(user).set("password", DigestUtils.sha512Hex(newPassword)).build());
            txn.commit();

            LOG.info("Password changed: " + userId);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("message", "Password changed successfully");
            return successResponse(data);
        } catch (DatastoreException e) {
            LOG.severe("changePassword error: " + e.getMessage());
            return errorResponse(ERR_FORBIDDEN);
        } finally { if (txn.isActive()) txn.rollback(); }
    }

    // ======================== Op10: LOGOUT ========================
    @POST
    @Path("/logout")
    public Response logout(String body) {
        LOG.fine("Op10: Logout");
        Map<String, Object> request = parseBody(body);
        if (request == null) return errorResponse(ERR_INVALID_INPUT);

        int[] err = new int[1];
        Entity session = requireValidToken(request, err);
        if (session == null) return errorResponse(err[0]);

        String callerRole = session.getString("role");
        String callerId = session.getString("userId");
        AuthToken token = getToken(request);

        Map<String, Object> input = getInput(request);
        if (input != null && input.get("userID") != null) {
            String targetUser = (String) input.get("userID");
            // USER and BOFFICER: own sessions only. ADMIN: any.
            if (!callerRole.equals("ADMIN") && !callerId.equals(targetUser))
                return errorResponse(ERR_UNAUTHORIZED);
        }

        Key sessionKey = datastore.newKeyFactory().setKind(KIND_SESSION).newKey(token.tokenId);
        datastore.delete(sessionKey);

        LOG.info("Logout: " + callerId);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("message", "Logout successful");
        return successResponse(data);
    }
}
