# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json


class GitConfigWriter:
    def __init__(self, options):
        self.options = options

    def remove_subsection(self, section, subsection):
        """
        Filter all the options defined in git config file,
        returning only the options that match the section specified.
        """
        self.options = list(
            filter(
                lambda tag: not (
                    tag["section"] == section and tag["subsection"] == subsection
                ),
                self.options,
            )
        )

    def list_section(self, section, subsection):
        """
        Filter all the options defined in git config file,
        returning only the options that match the section specified.
        """
        if subsection is not None:
            return list(
                filter(
                    lambda tag: tag["section"] == section
                    and tag["subsection"] == subsection,
                    self.options,
                )
            )
        else:
            return list(filter(lambda tag: tag["section"] == section, self.options))

    def get_config_as_string(self):
        """
        Return a string representation of the options as written in a git config file.
        """
        cfg_str = ""
        for section in self._get_all_section_names():
            cfg_str += self._get_section_as_string(
                section["section"], section.get("subsection", None)
            )
        return cfg_str

    def write_config(self, path):
        """
        Write the options as a git config file.
        """
        with open(path, "w") as file:
            file.write(self.get_config_as_string())

    def _get_all_section_names(self):
        sections = []
        for x in self.options:
            if x["subsection"] is not None:
                sections.append(
                    {"section": x["section"], "subsection": x["subsection"]}
                )
            else:
                sections.append({"section": x["section"]})
        section_names = set(json.dumps(i, sort_keys=True) for i in sections)
        return map(lambda s: json.loads(s), section_names)

    def _get_section_as_string(self, section, subsection):
        section_options = self.list_section(section, subsection)
        options_str = "\n\t".join(
            [f"{item['key']} = {item['value']}" for item in section_options]
        )
        output_section = section
        if subsection is not None:
            output_section += f' "{subsection}"'
        return f"[{output_section}]\n\t{options_str}\n"
