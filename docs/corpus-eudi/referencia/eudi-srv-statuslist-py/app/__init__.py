# coding: latin-1
###############################################################################
# Copyright (c) 2023 European Commission
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
###############################################################################
import os
from flask import Flask
from flask_cors import CORS
from dotenv import load_dotenv
from .lists_renewal import start_renewal_thread
from flask_swagger_ui import get_swaggerui_blueprint
from app.config_service import ConfService as cfgservice


def create_app():
    app = Flask(__name__)
    cors = CORS(app)


    load_dotenv()
    app.config['API_key'] = os.getenv("API_key")
    #print("API_Key from env: ", app.config['API_key'], flush=True)

    from app import status_list_endpoints

    SWAGGER_URL = "/token_status_list/swagger"
    API_URL = cfgservice.service_url + "token_status_list/static/swagger.json"
    swagger_ui_blueprint = get_swaggerui_blueprint(
        SWAGGER_URL, API_URL,
        config={"app_name": "My Flask API"}
    )

    # Register blueprints
    app.register_blueprint(status_list_endpoints.token)
    app.register_blueprint(swagger_ui_blueprint, url_prefix=SWAGGER_URL)

    app.debug = True

    start_renewal_thread()

    return app

