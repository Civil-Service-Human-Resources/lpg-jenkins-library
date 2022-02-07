import os
from mysql import connector
import logging

logging.basicConfig(format='%(asctime)s %(levelname)-8s %(message)s', datefmt='%Y-%m-%d %H:%M:%S', level=logging.INFO)

logger = logging.getLogger()

db_conn = {
    "user" :os.environ['SQL_USER'],
    "password" :os.environ['SQL_PASSWORD'],
    "host" :os.environ['SQL_HOST'],
    "port" :3306,
    "database": 'identity'
}

DELETE_BATCH = 1000

cnx = connector.connect(**db_conn)

all_tokens = "select count(*) from token"
all_valid_non_user_tokens = "select count(*) from token where status = 0 and user_name is null;"
all_invalid_non_user_tokens = "select count(*) from token where status = 1 and user_name is null;"
all_valid_user_tokens = "select count(*) from token where status = 0 and user_name is not null;"
all_invalid_user_tokens = "select count(*) from token where status = 1 and user_name is not null;"

DELETE_INVALID_TOKEN_SQL=f"delete from token where status = 1 limit {DELETE_BATCH};"
DELETE_VALID_USER_TOKEN_SQL=f"delete from token where status = 0 and user_name is not null limit {DELETE_BATCH};"

def run_count_query(sql):
    try:
        logger.info(f"Executing SQL:")
        logger.info(sql)
        cursor = cnx.cursor()
        cursor.execute(sql)
        count_res = cursor.fetchone()[0]
        logger.info(f"Count: {count_res}")
        return count_res
    except Exception as e:
        logger.error(f"Error counting tokens: {e}")

def delete_tokens(sql, token_count):

    try:
        cursor = cnx.cursor()

        while token_count > 0:
            logger.info(f"{token_count} tokens remaining. Deleting {DELETE_BATCH}")
            cursor.execute(sql)
            cnx.commit()
            token_count = token_count - DELETE_BATCH

        logger.info("Tokens deleted")
    
    except Exception as e:
        logger.error(f"Failed to delete tokens. Tokens remaining: {token_count}. Exception: {e}")


def run():

    logger.info("Beginning job")

    logger.info(f"Counting all tokens in token table")
    run_count_query(all_tokens)

    logger.info(f"Counting all valid non-user tokens")
    run_count_query(all_valid_non_user_tokens)

    logger.info(f"Counting all invalid non-user tokens")
    invalid_non_user_token_count = run_count_query(all_invalid_non_user_tokens)

    logger.info(f"Counting all invalid user tokens")
    valid_user_token_count = run_count_query(all_valid_user_tokens)

    logger.info(f"Counting all valid user tokens")
    invalid_user_token_count = run_count_query(all_invalid_user_tokens)

    invalid_token_total_count = invalid_non_user_token_count + invalid_user_token_count
    if invalid_token_total_count:
        logger.info(f"Deleting invalid tokens ({invalid_token_total_count})")
        delete_tokens(DELETE_INVALID_TOKEN_SQL, invalid_token_total_count)

    if valid_user_token_count:
        logger.info(f"Deleting valid user tokens ({valid_user_token_count})")
        delete_tokens(DELETE_VALID_USER_TOKEN_SQL, valid_user_token_count)

    logger.info("Job finished")

run()
